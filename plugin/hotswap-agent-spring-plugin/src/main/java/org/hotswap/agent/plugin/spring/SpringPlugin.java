/*
 * Copyright 2013-2019 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring;

import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.*;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacerTransformer;
import org.hotswap.agent.plugin.spring.getbean.ReloadSpringBeanTransformer;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerTransformer;
import org.hotswap.agent.plugin.spring.scanner.ClassPathBeanRefreshCommand;
import org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerTransformer;
import org.hotswap.agent.plugin.spring.scanner.XmlBeanRefreshCommand;
import org.hotswap.agent.plugin.spring.scanner.ext.BeanDefinitionPostProcessRegister;
import org.hotswap.agent.util.*;
import org.hotswap.agent.util.classloader.ClassLoaderHelper;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;
import org.hotswap.agent.watch.Watcher;

/**
 * Spring plugin.
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Spring", description = "Reload Spring configuration after class definition/change.",
        testedVersions = {"All between 3.0.1 - 5.2.2"}, expectedVersions = {"3x", "4x", "5x"},
        supportClass = {
                ClassPathBeanDefinitionScannerTransformer.class,
                ProxyReplacerTransformer.class,
                XmlBeanDefinitionScannerTransformer.class,
                ReloadSpringBeanTransformer.class,
                BeanDefinitionPostProcessRegister.class
        })
public class SpringPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringPlugin.class);

    /**
     * If a class is modified in IDE, sequence of multiple events is generated -
     * class file DELETE, CREATE, MODIFY, than Hotswap transformer is invoked.
     * ClassPathBeanRefreshCommand tries to merge these events into single command.
     * Wait this this timeout after class file event.
     */
    private static final int WAIT_ON_CREATE = 600;

    public static String[] basePackagePrefixes;

    @Init
    HotswapTransformer hotswapTransformer;

    @Init
    Watcher watcher;

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    public void init() {
        LOGGER.info("Spring plugin initialized");
        this.registerBasePackageFromConfiguration();
        this.initBasePackagePrefixes();
    }
    public void init(String version) {
        LOGGER.info("Spring plugin initialized - Spring core version '{}'", version);
        this.registerBasePackageFromConfiguration();
        this.initBasePackagePrefixes();
    }

    private void initBasePackagePrefixes() {
        PluginConfiguration pluginConfiguration = new PluginConfiguration(this.appClassLoader);
        if (basePackagePrefixes == null || basePackagePrefixes.length == 0) {
            basePackagePrefixes = pluginConfiguration.getBasePackagePrefixes();
        } else {
            String[] newBasePackagePrefixes = pluginConfiguration.getBasePackagePrefixes();
            List<String> both = new ArrayList<>(basePackagePrefixes.length + newBasePackagePrefixes.length);
            Collections.addAll(both, basePackagePrefixes);
            Collections.addAll(both, newBasePackagePrefixes);
            basePackagePrefixes = both.toArray(new String[both.size()]);
        }
    }

    @OnResourceFileEvent(path="/", filter = ".*.xml", events = {FileEvent.MODIFY})
    public void registerResourceListeners(URL url) {
        scheduler.scheduleCommand(new XmlBeanRefreshCommand(appClassLoader, url));
    }

    /**
     * register base package prefix from configuration file
     */
    public void registerBasePackageFromConfiguration() {
        if (basePackagePrefixes != null) {
            for (String basePackagePrefix : basePackagePrefixes) {
                this.registerBasePackage(basePackagePrefix);
            }
        }
    }

    private void registerBasePackage(final String basePackage) {
        final SpringChangesAnalyzer analyzer = new SpringChangesAnalyzer(appClassLoader);
        // v.d.: Force load/Initialize ClassPathBeanRefreshCommand classe in JVM. This is hack, in whatever reason sometimes new ClassPathBeanRefreshCommand()
        //       stays locked inside agent's transform() call. It looks like some bug in JVMTI or JVMTI-debugger() locks handling.
        hotswapTransformer.registerTransformer(appClassLoader, getClassNameRegExp(basePackage), new HaClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (classBeingRedefined != null) {
                    if (analyzer.isReloadNeeded(classBeingRedefined, classfileBuffer)) {
                        scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(classBeingRedefined.getClassLoader(),
                                basePackage, className, classfileBuffer));
                    }
                }
                return classfileBuffer;
            }

            @Override
            public boolean isForRedefinitionOnly() {
                return true;
            }
        });
    }

    /**
     * Register both hotswap transformer AND watcher - in case of new file the file is not known
     * to JVM and hence no hotswap is called. The file may even exist, but until is loaded by Spring
     * it will not be known by the JVM. File events are processed only if the class is not known to the
     * classloader yet.
     *
     * @param basePackage only files in a basePackage
     */
    public void registerComponentScanBasePackage(final String basePackage) {
        LOGGER.info("Registering basePackage {}", basePackage);

        this.registerBasePackage(basePackage);

        Enumeration<URL> resourceUrls = null;
        try {
            resourceUrls = getResources(basePackage);
        } catch (IOException e) {
            LOGGER.error("Unable to resolve base package {} in classloader {}.", basePackage, appClassLoader);
            return;
        }

        // for all application resources watch for changes
        while (resourceUrls.hasMoreElements()) {
            URL basePackageURL = resourceUrls.nextElement();

            if (!IOUtils.isFileURL(basePackageURL)) {
                LOGGER.debug("Spring basePackage '{}' - unable to watch files on URL '{}' for changes (JAR file?), limited hotswap reload support. " +
                        "Use extraClassPath configuration to locate class file on filesystem.", basePackage, basePackageURL);
                continue;
            } else {
                watcher.addEventListener(appClassLoader, basePackageURL, new WatchEventListener() {
                    @Override
                    public void onEvent(WatchFileEvent event) {
                        if (event.isFile() && event.getURI().toString().endsWith(".class")) {
                            // check that the class is not loaded by the classloader yet (avoid duplicate reload)
                            String className;
                            try {
                                className = IOUtils.urlToClassName(event.getURI());
                            } catch (IOException e) {
                                LOGGER.trace("Watch event on resource '{}' skipped, probably Ok because of delete/create event sequence (compilation not finished yet).", e, event.getURI());
                                return;
                            }
                            if (!ClassLoaderHelper.isClassLoaded(appClassLoader, className)) {
                                // refresh spring only for new classes
                                scheduler.scheduleCommand(new ClassPathBeanRefreshCommand(appClassLoader,
                                        basePackage, className, event), WAIT_ON_CREATE);
                            }
                        }
                    }
                });
            }
        }
    }

    private String getClassNameRegExp(String basePackage) {
        String regexp = basePackage;
        while (regexp.contains("**")) {
            regexp = regexp.replace("**", ".*");
        }
        if (!regexp.endsWith(".*")) {
            regexp += ".*";
        }
        return regexp;
    }

    private Enumeration<URL> getResources(String basePackage) throws IOException {
        String resourceName = basePackage;
        int index = resourceName.indexOf('*');
        if (index != -1) {
            resourceName = resourceName.substring(0, index);
            index = resourceName.lastIndexOf('.');
            if (index != -1) {
                resourceName = resourceName.substring(0, index);
            }
        }
        resourceName = resourceName.replace('.', '/');
        return appClassLoader.getResources(resourceName);
    }

    /**
     * Plugin initialization is after Spring has finished its startup and freezeConfiguration is called.
     *
     * This will override freeze method to init plugin - plugin will be initialized and the configuration
     * remains unfrozen, so bean (re)definition may be done by the plugin.
     *
     * 插件初始化是在Spring完成启动后调用的，它是freezeConfiguration。
     * 这将覆盖init插件的冻结方法-插件将被初始化，并且配置保持未冻结状态，因此可以通过插件完成bean（重新）定义。
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.DefaultListableBeanFactory")
    public static void register(CtClass clazz) throws NotFoundException, CannotCompileException {
        /*
         * {
         *      setCacheBeanMetadata(false);
         *      PluginManager.getInstance().getPluginRegistry().initializePlugin("org.hotswap.agent.plugin.spring.SpringPlugin", getClass().getClassLoader());
         *      try {
         *          ClassLoader __pluginClassLoader = PluginManager.class.getClassLoader();
         *          Object __pluginInstance = PluginManager.getInstance().getPlugin(org.hotswap.agent.plugin.spring.SpringPlugin.class.getName(), getClass().getClassLoader());
         *          Class __pluginClass = __pluginClassLoader.loadClass("org.hotswap.agent.plugin.spring.SpringPlugin");
         *          Class[] paramTypes = new Class[1];
         *          paramTypes[0] = __pluginClassLoader.loadClass("java.lang.String");
         *          java.lang.reflect.Method __callPlugin = __pluginClass.getDeclaredMethod("init", paramTypes);
         *          Object[] params = new Object[1];
         *          params[0] = org.springframework.core.SpringVersion.getVersion();
         *          __callPlugin.invoke(__pluginInstance, params);
         *      } catch (Exception e) {
         *          throw new Error(e);
         *      }
         * }
         *
         * 在 DefaultListableBeanFactory 构造方法之前插入上述代码。
         */
        StringBuilder src = new StringBuilder("{");
        src.append("setCacheBeanMetadata(false);");
        // init a spring plugin with every appclassloader
        src.append(PluginManagerInvoker.buildInitializePlugin(SpringPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(SpringPlugin.class, "init",
                "org.springframework.core.SpringVersion.getVersion()", String.class.getName()));
        src.append("}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertBeforeBody(src.toString());
        }

        // freezeConfiguration cannot be disabled because of performance degradation
        // instead call freezeConfiguration after each bean (re)definition and clear all caches.

        // WARNING - allowRawInjectionDespiteWrapping is not safe, however without this
        //   spring was not able to resolve circular references correctly.
        //   However, the code in AbstractAutowireCapableBeanFactory.doCreateBean() in debugger always
        //   showed that exposedObject == earlySingletonReference and hence everything is Ok.
        // 				if (exposedObject == bean) {
        //                  exposedObject = earlySingletonReference;
        //   The waring is because I am not sure what is going on here.

        // 由于性能下降，不能禁用freezeConfiguration，而是在每个bean（重新）定义之后调用freezeConfiguration并清除所有缓存。
        // 警告-allowRawInjectionDespiteWrapping是不安全的，但是如果没有这个 spring 将无法正确解析循环引用。
        // 但是，调试器中的AbstractAutowireCapableBeanFactory.doCreateBean（）中的代码始终显示暴露对象== earlySingletonReference，因此一切正常。
        // if（exposedObject == bean）{
        //     暴露的对象= earlySingletonReference;
        // 该警告是因为我不确定这里发生了什么。

        // 在 DefaultListableBeanFactory.freezeConfiguration() 之前插入下面的代码：
        // org.hotswap.agent.plugin.spring.ResetSpringStaticCaches#resetBeanNamesByType(this); 功能：把 singletonBeanNamesByType 清空了。
        // setAllowRawInjectionDespiteWrapping(true);
        CtMethod method = clazz.getDeclaredMethod("freezeConfiguration");
        method.insertBefore(
                "org.hotswap.agent.plugin.spring.ResetSpringStaticCaches.resetBeanNamesByType(this); " +
                        "setAllowRawInjectionDespiteWrapping(true); ");
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.aop.framework.CglibAopProxy")
    public static void cglibAopProxyDisableCache(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod method = ctClass.getDeclaredMethod("createEnhancer");
        // setBody : 将方法的内容设置为要写入的代码，当方法被 abstract修饰时，该修饰符被移除；表示不使用 cache？
        method.setBody("{" +
                "org.springframework.cglib.proxy.Enhancer enhancer = new org.springframework.cglib.proxy.Enhancer();" +
                "enhancer.setUseCache(false);" +
                "return enhancer;" +
                "}");

        LOGGER.debug("org.springframework.aop.framework.CglibAopProxy - cglib Enhancer cache disabled");
    }
}
