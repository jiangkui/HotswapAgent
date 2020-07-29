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
package org.hotswap.agent.plugin.mybatis.transformers;

import org.apache.ibatis.javassist.bytecode.AccessFlag;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.MyBatisPlugin;
import org.hotswap.agent.plugin.mybatis.proxy.ConfigurationProxy;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Static transformers for MyBatis plugin.
 *
 * @author Vladimir Dvorak
 */
public class MyBatisTransformers {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MyBatisTransformers.class);

    public static final String SRC_FILE_NAME_FIELD = "$$ha$srcFileName";
    public static final String REFRESH_DOCUMENT_METHOD = "$$ha$refreshDocument";
    public static final String REFRESH_METHOD = "$$ha$refresh";

    /**
     *  XPathParser：XpathParser的作用是提供根据Xpath表达式获取基本的DOM节点Node信息的操作。
     *
     * {
     *     this.$$ha$srcFileName = org.hotswap.agent.util.IOUtils.extractFileNameFromInputSource($1);
     * }
     * public boolean $$ha$refreshDocument() {
     *    if(this.$$ha$srcFileName!=null) {
     *        this.document = createDocument(new org.xml.sax.InputSource(new java.io.FileReader(this.$$ha$srcFileName)));
     *        return true;
     *    }
     *    return false;
     * }
     */
//    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.parsing.XPathParser")
    public static void patchXPathParser(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtClass stringClass = classPool.get("java.lang.String");
        CtField sourceFileField = new CtField(stringClass, SRC_FILE_NAME_FIELD, ctClass);
        ctClass.addField(sourceFileField);

        CtMethod method = ctClass.getDeclaredMethod("createDocument");
        method.insertBefore("{" +
                "this." + SRC_FILE_NAME_FIELD + " = " + org.hotswap.agent.util.IOUtils.class.getName() + ".extractFileNameFromInputSource($1);" +
                "}"
        );

        //{org.hotswap.agent.config.PluginManager.getInstance().getPluginRegistry().initializePlugin("com.sankuai.meituan.oasis.bdf.dal.hotswap.MybatisSpringPlugin", getClass().getClassLoader());}
        CtMethod newMethod = CtNewMethod.make(
                "public boolean " + REFRESH_DOCUMENT_METHOD + "() {" +
                        "if(this." + SRC_FILE_NAME_FIELD + "!=null) {" +
                        "this.document=createDocument(new org.xml.sax.InputSource(new java.io.FileReader(this." + SRC_FILE_NAME_FIELD + ")));" +
                        "return true;" +
                        "}" +
                        "return false;" +
                        "}", ctClass);
        ctClass.addMethod(newMethod);
        LOGGER.debug("org.apache.ibatis.parsing.XPathParser patched.");
    }

    /**
     * XMLConfigBuilder、XMLMapperBuilder 等 XyzBuilder 的父类，提供了对
     * typeAliases（domain 实体类型转换，某些通用转换，看下 org.apache.ibatis.session.Configuration#Configuration() 的构造方法）
     * 和 typeHandlers（出入参转换，貌似可自定义） 等支持
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.builder.BaseBuilder")
    public static void patchBaseBuilder(CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("org.apache.ibatis.builder.BaseBuilder patched.");
        CtField configField = ctClass.getField("configuration");
        configField.setModifiers(configField.getModifiers() & ~AccessFlag.FINAL);
    }

    /**
     * Mybatis-config.xml --> Configuration 对象，由 SqlSessionFactory 持有。
     *
     * Mybatis 初始化流程：https://www.jianshu.com/p/ec40a82cae28
     * 1、调用 SqlSessionFactoryBuilder 对象的 build(inputStream) 方法；
     * 2、SqlSessionFactoryBuilder 会根据输入流 inputStream 等信息创建 XMLConfigBuilder 对象;
     * 3、SqlSessionFactoryBuilder 调用 XMLConfigBuilder 对象的 parse() 方法；
     * 4、XMLConfigBuilder 对象返回 Configuration 对象；
     * 5、SqlSessionFactoryBuilder 根据 Configuration 对象创建一个 DefaultSessionFactory 对象；
     * 6、SqlSessionFactoryBuilder 返回 DefaultSessionFactory 对象给 Client，供 Client 使用
     *
     * {
     *    org.hotswap.agent.config.PluginManager.getInstance().getPluginRegistry().initializePlugin("org.hotswap.agent.plugin.mybatis.MyBatisPlugin", getClass().getClassLoader());
     *    try {
     *        ClassLoader __pluginClassLoader = org.hotswap.agent.config.PluginManager.class.getClassLoader();
     *        Object __pluginInstance = org.hotswap.agent.config.PluginManager.getInstance().getPlugin(org.hotswap.agent.plugin.mybatis.MyBatisPlugin.class.getName(), getClass().getClassLoader());
     *        Class __pluginClass = __pluginClassLoader.loadClass("org.hotswap.agent.plugin.mybatis.MyBatisPlugin");
     *        Class[] paramTypes = new Class[2];
     *        paramTypes[0] = __pluginClassLoader.loadClass("java.lang.String");
     *        paramTypes[1] = __pluginClassLoader.loadClass("java.lang.Object");
     *        java.lang.reflect.Method __callPlugin = __pluginClass.getDeclaredMethod("registerConfigurationFile", paramTypes);
     *        Object[] params = new Object[2];
     *        params[0] = org.hotswap.agent.plugin.mybatis.transformers.XPathParserCaller.getSrcFileName(this.parser);
     *        params[1] = this;
     *        __callPlugin.invoke(__pluginInstance, params);
     *    } catch (Exception e) {
     *        throw new Error(e);
     *    }
     *    this.configuration = org.hotswap.agent.plugin.mybatis.proxy.ConfigurationProxy.getWrapper(this).proxy(this.configuration);
     * }
     *
     * public void $$ha$refresh() {
     *    if(org.hotswap.agent.plugin.mybatis.transformers.XPathParserCaller.refreshDocument(this.parser)) {
     *        this.parsed=false;
     *        parse();
     *    }
     * }
     */
//    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.builder.xml.XMLConfigBuilder")
    public static void patchXMLConfigBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {

        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(MyBatisPlugin.class, "registerConfigurationFile",
                XPathParserCaller.class.getName() + ".getSrcFileName(this.parser)", "java.lang.String", "this", "java.lang.Object"));
        src.append("this.configuration = " + ConfigurationProxy.class.getName() + ".getWrapper(this).proxy(this.configuration);");
        src.append("}");

        CtClass[] constructorParams = new CtClass[] {
                classPool.get("org.apache.ibatis.parsing.XPathParser"),
                classPool.get("java.lang.String"),
                classPool.get("java.util.Properties")
        };

        ctClass.getDeclaredConstructor(constructorParams).insertAfter(src.toString());
        CtMethod newMethod = CtNewMethod.make(
                "public void " + REFRESH_METHOD + "() {" +
                        "if(" + XPathParserCaller.class.getName() + ".refreshDocument(this.parser)) {" +
                        "this.parsed=false;" +
                        "parse();" +
                        "}" +
                        "}", ctClass);
        ctClass.addMethod(newMethod);
        LOGGER.debug("org.apache.ibatis.builder.xml.XMLConfigBuilder patched.");
    }

    /**
     * XMLMapperBuilder 对 xml 定义进行解析
     *
     * {
     *     org.hotswap.agent.config.PluginManager.getInstance().getPluginRegistry().initializePlugin("org.hotswap.agent.plugin.mybatis.MyBatisPlugin", getClass().getClassLoader());
     *     try {
     *         ClassLoader __pluginClassLoader = org.hotswap.agent.config.PluginManager.class.getClassLoader();
     *         Object __pluginInstance = org.hotswap.agent.config.PluginManager.getInstance().getPlugin(org.hotswap.agent.plugin.mybatis.MyBatisPlugin.class.getName(), getClass().getClassLoader());
     *         Class __pluginClass = __pluginClassLoader.loadClass("org.hotswap.agent.plugin.mybatis.MyBatisPlugin");
     *         Class[] paramTypes = new Class[2];
     *         paramTypes[0] = __pluginClassLoader.loadClass("java.lang.String");
     *         paramTypes[1] = __pluginClassLoader.loadClass("java.lang.Object");
     *         java.lang.reflect.Method __callPlugin = __pluginClass.getDeclaredMethod("registerConfigurationFile", paramTypes);
     *         Object[] params = new Object[2];
     *         params[0] = org.hotswap.agent.plugin.mybatis.transformers.XPathParserCaller.getSrcFileName(this.parser);
     *         params[1] = this;
     *         __callPlugin.invoke(__pluginInstance, params);
     *     } catch (Exception e) {
     *         throw new Error(e);
     *     }
     *  }
     */
//    @OnClassLoadEvent(classNameRegexp = "org.apache.ibatis.builder.xml.XMLMapperBuilder")
    public static void patchXMLMapperBuilder(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        src.append(PluginManagerInvoker.buildInitializePlugin(MyBatisPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(MyBatisPlugin.class, "registerConfigurationFile",
                XPathParserCaller.class.getName() + ".getSrcFileName(this.parser)", "java.lang.String", "this", "java.lang.Object"));
        src.append("}");

        CtClass[] constructorParams = new CtClass[] {
                classPool.get("org.apache.ibatis.parsing.XPathParser"),
                classPool.get("org.apache.ibatis.session.Configuration"),
                classPool.get("java.lang.String"),
                classPool.get("java.util.Map")
        };

        CtConstructor constructor = ctClass.getDeclaredConstructor(constructorParams);
        constructor.insertAfter(src.toString());
        LOGGER.debug("org.apache.ibatis.builder.xml.XMLMapperBuilder patched.");
    }
}
