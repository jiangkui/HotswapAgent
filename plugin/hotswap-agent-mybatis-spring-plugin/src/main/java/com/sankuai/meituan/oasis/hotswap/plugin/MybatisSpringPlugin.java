package com.sankuai.meituan.oasis.hotswap.plugin;

import org.apache.ibatis.javassist.bytecode.AccessFlag;
import org.hotswap.agent.annotation.*;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.net.URL;

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

    @OnClassLoadEvent(classNameRegexp = "org.mybatis.spring.SqlSessionFactoryBean")
    public static void initPlugin(CtClass ctClass, ClassPool classPool) {
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
        LOGGER.debug("org.mybatis.spring.SqlSessionFactoryBean patched.");
    }

    /**
     * TODO jiangkui 一个想法。
     */
    public void idea() {
        // 1、黑掉 Configuration
        // 2、修改 Configuration 内的 StrictMap 能重复添加
        // 3、增加一个方法 refreshXMLResource()，接受一个 resource，修正resource 路径（别重复），重新 parse()，并加入到 configuration 内。
        // 4、多数据源如何处理？参考 mybatis plugin 的搞法，注册一个 cache
        // 5、与 mybatis plugin 的互相影响？ 俩应该只能有一个能正式使用。
        // 6、进阶：annotation 形式的 sql 如何处理？
        // 7、进阶：增加 mapper 方法，如何处理引用相关？（class、bean、等等）
    }

    /**
     * 修改 DefaultSqlSessionFactory 内的 configuration 为非 final 字段。
     * @param ctClass
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.session.defaults.DefaultSqlSessionFactory")
    public static void patchBaseBuilder(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("org.apache.ibatis.session.defaults.DefaultSqlSessionFactory patched.");
        CtField configField = ctClass.getField("configuration");
        configField.setModifiers(configField.getModifiers() & ~AccessFlag.FINAL);
    }

    /**
     * 如果 configuration.xml 或 mapper.xml 有变更，则重新加载 configuration
     */
    @OnResourceFileEvent(path="/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) {
        RefreshSqlSessionFactoryBean.refresh(url);
    }
}
