package org.hotswap.agent.plugin.spring.scanner.ext;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public class BeanDefinitionPostProcessManager {
    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanDefinitionPostProcessManager.class);

    private static List<BeanDefinitionPostProcessor> beanDefinitionPostProcessorList ;

    /**
     * 请在有对应 Class 文件加载时，注册对应的处理器。
     *
     * @param beanDefinitionPostProcessor
     */
    public static void register(BeanDefinitionPostProcessor beanDefinitionPostProcessor) {
        if (beanDefinitionPostProcessorList == null) {
            beanDefinitionPostProcessorList = new ArrayList<>();
        }
        beanDefinitionPostProcessorList.add(beanDefinitionPostProcessor);
    }

    /**
     * 调用 BeanDefinition 的后置处理器
     *
     * 使用场景：Zebra Scanner 和 MybatisSpring 的 Scanner 都会对扫描后的 BeanDefinition 做二次加工，为了方便扩展，就定义了此方法。
     */
    public static void applyPostProcessAfterScanned(BeanDefinitionHolder beanDefinitionHolder, ClassPathBeanDefinitionScanner scanner) {
        for (BeanDefinitionPostProcessor beanDefinitionPostProcessor : beanDefinitionPostProcessorList) {
            try {
                beanDefinitionPostProcessor.postProcessAfterScanned(beanDefinitionHolder, scanner);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
