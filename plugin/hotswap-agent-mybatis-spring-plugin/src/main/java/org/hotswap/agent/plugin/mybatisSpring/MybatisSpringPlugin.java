package org.hotswap.agent.plugin.mybatisSpring;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.hotswap.agent.annotation.*;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mybatis Spring Plugin
 *
 * @author jiangkui
 * @since 1.5.0
 */
@Plugin(name = "MybatisSpring",
        description = "Reload SqlSessionFactory after configuration create/change.",
        testedVersions = {"All between 1.3.1"},
        expectedVersions = {"1.3.1" })
public class MybatisSpringPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MybatisSpringPlugin.class);

    private static List<Configuration> configurationList = new ArrayList<Configuration>();

    @Init
    public void init() {
        LOGGER.info("\n" +
                "  __  ____     ______       _______ _____  _____    _____ _____  _____  _____ _   _  _____   _____  _     _    _  _____ _____ _   _ \n" +
                " |  \\/  \\ \\   / /  _ \\   /\\|__   __|_   _|/ ____|  / ____|  __ \\|  __ \\|_   _| \\ | |/ ____| |  __ \\| |   | |  | |/ ____|_   _| \\ | |\n" +
                " | \\  / |\\ \\_/ /| |_) | /  \\  | |    | | | (___   | (___ | |__) | |__) | | | |  \\| | |  __  | |__) | |   | |  | | |  __  | | |  \\| |\n" +
                " | |\\/| | \\   / |  _ < / /\\ \\ | |    | |  \\___ \\   \\___ \\|  ___/|  _  /  | | | . ` | | |_ | |  ___/| |   | |  | | | |_ | | | | . ` |\n" +
                " | |  | |  | |  | |_) / ____ \\| |   _| |_ ____) |  ____) | |    | | \\ \\ _| |_| |\\  | |__| | | |    | |___| |__| | |__| |_| |_| |\\  |\n" +
                " |_|  |_|  |_|  |____/_/    \\_\\_|  |_____|_____/  |_____/|_|    |_|  \\_\\_____|_| \\_|\\_____| |_|    |______\\____/ \\_____|_____|_| \\_|\n" +
                "                                                                                                                                    \n" +
                "                                                                                                                                    \n");
    }

    /**
     * 修改 SqlSessionFactory 字节码
     */
    @OnClassLoadEvent(classNameRegexp = "org.mybatis.spring.SqlSessionFactoryBean")
    public static void patchSqlSessionFactoryBean(CtClass ctClass, ClassPool classPool) throws Exception {
        initMybatisSpringPlugin(ctClass, classPool);
        registerCollectionConfigurationCode(ctClass, classPool);
        LOGGER.debug("org.mybatis.spring.SqlSessionFactoryBean patched.");
    }

    /**
     * 注入代码：初始化 MybatisSpringPlugin 插件
     */
    private static void initMybatisSpringPlugin(CtClass ctClass, ClassPool classPool) {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(MybatisSpringPlugin.class));
        src.append("}");

        CtConstructor[] constructors = ctClass.getConstructors();
        if (constructors != null) {
            for (CtConstructor constructor : constructors) {
                try {
                    constructor.insertAfter(src.toString());
                } catch (CannotCompileException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 注入代码：用于收集 Configuration 对象
     */
    private static void registerCollectionConfigurationCode(CtClass ctClass, ClassPool classPool) throws Exception {
        String insertCode = MybatisSpringPlugin.class.getName() + ".registerConfiguration(this.sqlSessionFactory);";
        CtMethod buildSqlSessionFactory = ctClass.getDeclaredMethod("afterPropertiesSet");
        buildSqlSessionFactory.insertAfter(insertCode);
    }

    /**
     * 回调代码，收集 Configuration 对象。
     *
     * SqlSessionFactoryBean#buildSqlSessionFactory 执行之后回调。
     * @param sqlSessionFactory sqlSessionFactory
     */
    public static void registerConfiguration(SqlSessionFactory sqlSessionFactory) {
        if (sqlSessionFactory == null || sqlSessionFactory.getConfiguration() == null) {
            LOGGER.info("Configuration 为空！");
            return;
        }

        LOGGER.info("Configuration 注册！" + sqlSessionFactory.getConfiguration());
        configurationList.add(sqlSessionFactory.getConfiguration());
    }

    /**
     * -javaagent:/Users/jiangkui/StudyProject/HotswapAgent/hotswap-agent/target/hotswap-agent.jar
     * -javaagent:/Users/jiangkui/StudyProject/HotswapAgent/hotswap-agent-core/target/hotswap-agent-core-0.0.1-SNAPSHOT.jar
     *
     * 对 Configuration.StrictMap 进行修改
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.session.Configuration.StrictMap")
    public static void patchConfigurationStrictMap(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        // Configuration.StrictMap 的 put 方法不支持重复添加，这里粗糙的简单处理下。使之能重复添加。
        String insertBeforeCode = "{remove(key);}";

        CtClass[] constructorParams = new CtClass[] {
                classPool.get("java.lang.String"),
                classPool.get("java.lang.Object")
        };

        CtMethod putMethod = ctClass.getDeclaredMethod("put", constructorParams);
        putMethod.insertBefore(insertBeforeCode);
        LOGGER.debug("org.apache.ibatis.session.Configuration.StrictMap patched.");
    }

    /**
     * 如果 configuration.xml 或 mapper.xml 有变更，则重新加载 configuration
     *
     * 3、增加一个方法 refreshXMLResource()，接受一个 resource，修正resource 路径（别重复），重新 parse()，并加入到 configuration 内。
     * 4、多数据源如何处理？参考 mybatis plugin 的搞法，注册一个 cache
     * 5、与 mybatis plugin 的互相影响？ 俩应该只能有一个能正式使用。
     * 6、进阶：annotation 形式的 sql 如何处理？
     * 7、进阶：增加 mapper 方法，如何处理引用相关？（class、bean、等等）
     */
    // fixme jiangkui 正则路径改为 *.xml 和 *.class
    @OnResourceFileEvent(path="/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) {
        try {
            // fixme jiangkui 大概步骤。
            // xml 和 annotation 过滤，过滤掉无用的路径。（需要从 Configuration 中一次性搂出所有的 resource），这些才是要监听并处理的。
            // 判断是 xml 还是 annotation
            // 分别处理
            // xml：路径映射匹配（这一步貌似可以省略，Configuration 内的 MappedStatement 是个 map，能根据id覆盖）
            // annotation：路径映射匹配

            LOGGER.info(url.getPath() + "刷新！");
            String source = adapterPath(url);
            Configuration configuration = findConfiguration(source);
            removeLoadedMark(url, source, configuration);
            reloadResource(url, source, configuration);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private Configuration findConfiguration(String source) {
        return configurationList.get(0);
    }

    /**
     * 适配 url
     *
     * 因为存在本地热部署和远程热部署的情况，所以 url 不一定完全相同，需要做一层转换。
     *
     * @param needTransformerUrl 需要转换的 url
     * @return 转换后的 url
     */
    private String adapterPath(URL needTransformerUrl) {

        //fixme jiangkui 路径匹配与识别
        return needTransformerUrl.getPath();
    }

    /**
     * 删掉 resource 已加载的标记
     */
    private void removeLoadedMark(URL url, String resource, Configuration configuration) throws Exception {
        /*
        原因是被 mybatis plugin 代理了，所以拿不到 field。暂时取消 mybatis plugin。
        HOTSWAP AGENT: 16:30:25.589 ERROR (org.hotswap.agent.plugin.mybatisSpring.MybatisSpringPlugin) - loadedResources
        java.lang.NoSuchFieldException: loadedResources
            at java.lang.Class.getDeclaredField(Class.java:2070)
            at org.hotswap.agent.plugin.mybatisSpring.MybatisSpringPlugin.removeLoadedMark(MybatisSpringPlugin.java:175)
            at org.hotswap.agent.plugin.mybatisSpring.MybatisSpringPlugin.registerResourceListeners(MybatisSpringPlugin.java:148)
            at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
            at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
            at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
            at java.lang.reflect.Method.invoke(Method.java:498)
            at org.hotswap.agent.annotation.handler.WatchEventCommand.onWatchEvent(WatchEventCommand.java:190)
            at org.hotswap.agent.annotation.handler.WatchEventCommand.executeCommand(WatchEventCommand.java:98)
            at org.hotswap.agent.command.impl.CommandExecutor.run(CommandExecutor.java:43)
         */
        Field loadedResourcesField = configuration.getClass().getDeclaredField("loadedResources");
        loadedResourcesField.setAccessible(true);
        Set loadedResourcesSet = ((Set) loadedResourcesField.get(configuration));
        loadedResourcesSet.remove(resource);
    }

    /**
     * 重新加载 xml
     * @param url 要 reload 的xml
     * @param resource 资源标记位
     * @param configuration
     */
    private void reloadResource(URL url, String resource, Configuration configuration) throws Exception {
        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(url.openConnection().getInputStream(), configuration,
                resource, configuration.getSqlFragments());
        xmlMapperBuilder.parse();
    }
}
