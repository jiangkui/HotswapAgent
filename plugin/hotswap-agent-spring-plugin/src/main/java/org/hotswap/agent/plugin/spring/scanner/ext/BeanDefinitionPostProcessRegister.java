package org.hotswap.agent.plugin.spring.scanner.ext;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.scanner.ext.impl.MybatisSpringMapperBeanDefinitionPostProcessor;
import org.hotswap.agent.plugin.spring.scanner.ext.impl.ZebraMapperBeanDefinitionPostProcessor;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public class BeanDefinitionPostProcessRegister {

    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDefinitionPostProcessRegister.class);

    @OnClassLoadEvent(classNameRegexp = MybatisSpringMapperBeanDefinitionPostProcessor.MYBATIS_SCANNER)
    public static void registerMybatisScannerPostProcessor() throws NotFoundException, CannotCompileException {
        LOGGER.info ( "注册 MybatisSpringMapperBeanDefinitionPostProcessor。" );
        BeanDefinitionPostProcessManager.register(new MybatisSpringMapperBeanDefinitionPostProcessor());
    }

    @OnClassLoadEvent(classNameRegexp = ZebraMapperBeanDefinitionPostProcessor.ZEBRA_SCANNER)
    public static void registerZebraScannerPostProcessor() throws NotFoundException, CannotCompileException {
        LOGGER.info ( "注册 ZebraMapperBeanDefinitionPostProcessor。" );
        BeanDefinitionPostProcessManager.register(new ZebraMapperBeanDefinitionPostProcessor());
    }
}
