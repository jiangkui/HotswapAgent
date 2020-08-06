package org.hotswap.agent.plugin.spring.scanner.ext.impl;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.scanner.ext.BeanDefinitionPostProcessor;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Mybatis Spring Scanner 兼容处理器
 *
 * TODO jiangkui 此方法尚未测试
 *
 * @author jiangkui
 * @since 1.5.0
 */
public class MybatisSpringMapperBeanDefinitionPostProcessor implements BeanDefinitionPostProcessor {

    private static AgentLogger LOGGER = AgentLogger.getLogger(MybatisSpringMapperBeanDefinitionPostProcessor.class);

    public static final String MYBATIS_SCANNER = "org.mybatis.spring.mapper.ClassPathMapperScanner";

    /**
     * MybatisSpring Scanner 扫描 Mapper 文件生成 BeanDefinition 后，做的加工处理
     *
     * 详情参见 Mybatis Scanner 源码：org.mybatis.spring.mapper.ClassPathMapperScanner#processBeanDefinitions(java.util.Set)
     *
     * @param beanDefinitionHolder BeanDefinition
     * @param scanner 扫描并生成此 BeanDefinition 的扫描器
     */
    @Override
    public void postProcessAfterScanned(BeanDefinitionHolder beanDefinitionHolder, ClassPathBeanDefinitionScanner scanner) {
        if (scanner == null) {
            return;
        }
        if (!MYBATIS_SCANNER.equals(scanner.getClass().getName())) {
            return;
        }

        try {
            Method processBeanDefinitionsMethod = scanner.getClass().getDeclaredMethod("processBeanDefinitions", Set.class);

            Set<BeanDefinitionHolder> beanDefinitions = new HashSet<>();
            beanDefinitions.add(beanDefinitionHolder);

            // 直接反射调用 MybatisSpring Scanner 的后续处理方法
            processBeanDefinitionsMethod.invoke(scanner, beanDefinitions);
            LOGGER.info(MYBATIS_SCANNER + " postProcessAfterScanned success! beanName：" + beanDefinitionHolder.getBeanName());
        } catch (Exception e) {
            LOGGER.error(MYBATIS_SCANNER + " postProcessAfterScanned fail! beanName：" + beanDefinitionHolder.getBeanName(), e);
        }
    }
}
