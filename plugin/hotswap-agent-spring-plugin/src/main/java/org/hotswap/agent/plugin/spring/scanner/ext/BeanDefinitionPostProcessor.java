package org.hotswap.agent.plugin.spring.scanner.ext;

import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

/**
 * BeanDefinition 的后置处理器
 *
 * @author jiangkui
 * @since 1.0.0
 */
public interface BeanDefinitionPostProcessor {

    /**
     * scanner 扫描完成后，对 BeanDefinition 做进一步加工
     *
     * @param beanDefinitionHolder BeanDefinition
     * @param scanner 扫描并生成此 BeanDefinition 的扫描器
     */
    void postProcessAfterScanned(BeanDefinitionHolder beanDefinitionHolder, ClassPathBeanDefinitionScanner scanner);
}
