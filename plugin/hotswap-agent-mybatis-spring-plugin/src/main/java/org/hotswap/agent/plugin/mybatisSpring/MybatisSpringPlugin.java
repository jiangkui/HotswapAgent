package org.hotswap.agent.plugin.mybatisSpring;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.hotswap.agent.annotation.*;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatisSpring.resource.MybatisResource;
import org.hotswap.agent.plugin.mybatisSpring.resource.MybatisResourceManager;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Mybatis Spring Plugin
 *
 * @author jiangkui
 * @since 1.5.0
 */
@Plugin(name = "MybatisSpring",
        description = "Reload SqlSessionFactory after configuration create/change.",
        testedVersions = {"All between 1.3.1"},
        expectedVersions = {"1.3.1"})
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
     * <p>
     * SqlSessionFactoryBean#buildSqlSessionFactory 执行之后回调。
     *
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
     * <p>
     * 对 Configuration.StrictMap 进行修改
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.session.Configuration.StrictMap")
    public static void patchConfigurationStrictMap(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        // Configuration.StrictMap 的 put 方法不支持重复添加，这里粗糙的简单处理下。使之能重复添加。
        String insertBeforeCode = "{remove(key);}";

        CtClass[] constructorParams = new CtClass[]{
                classPool.get("java.lang.String"),
                classPool.get("java.lang.Object")
        };

        CtMethod putMethod = ctClass.getDeclaredMethod("put", constructorParams);
        putMethod.insertBefore(insertBeforeCode);
        LOGGER.debug("org.apache.ibatis.session.Configuration.StrictMap patched.");
    }

    /**
     * Spring 容器加载完毕后，初始化 mybatis 的路径
     *
     * 用途：热部署时，xml 和 class 会有很多，需要根据此路径来判断是否是 mybatis 的热部署
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.context.support.AbstractApplicationContext")
    public static void patchApplicationContext(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        String insertCode = MybatisSpringPlugin.class.getName() + ".initMybatisResourceManager();";
        CtMethod buildSqlSessionFactory = ctClass.getDeclaredMethod("refresh");
        buildSqlSessionFactory.insertAfter(insertCode);
    }
    public static void initMybatisResourceManager() {
        MybatisResourceManager.init(configurationList);
    }

    /**
     * mybatis mapper.xml 变更刷新
     * <p>
     * 会先判断是否为 mybatis 的 xml 文件，之后再尝试热加载
     *
     * @param url 变更的资源路径，类似这种：file:/Users/jiangkui/StudyProject/HotswapAgent/plugin/hotswap-agent-mybatis-spring-plugin/target/test-classes/org/hotswap/agent/plugin/mybatisSpring/Mapper.xml
     */
    @OnResourceFileEvent(path = "/", filter = ".*.xml$", events = {FileEvent.MODIFY})
    public void reloadXML(URL url) {
        try {
            MybatisResource mybatisResource = MybatisResourceManager.findXmlResource(url);
            if (mybatisResource == null) {
                return;
            }

            LOGGER.info("Mybatis XML 形式热加载" + url.getPath());
            mybatisResource.reload(url);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * mybatis 注解sql 变更刷新
     * <p>
     * <p>
     * 4、多数据源如何处理？参考 mybatis plugin 的搞法，注册一个 cache
     * 5、与 mybatis plugin 的互相影响？ 俩应该只能有一个能正式使用。
     * 6、进阶：annotation 形式的 sql 如何处理？
     * 7、进阶：增加 mapper 方法，如何处理引用相关？（class、bean、等等）
     * <p>
     * 热加载之前，会先过滤掉非 mapper class
     *
     * @param ctClass 变更的 class
     */
    @OnClassLoadEvent(classNameRegexp = ".*")
    public void reloadAnnotation(Class redefiningClass, CtClass ctClass, ClassPool classPool) throws Exception {
        try {
            MybatisResource mybatisResource = MybatisResourceManager.findAnnotationResource(ctClass);
            if (mybatisResource == null) {
                return;
            }
            LOGGER.info("Mybatis 注解形式热加载：" + ctClass.getName());
            mybatisResource.reload(redefiningClass);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}