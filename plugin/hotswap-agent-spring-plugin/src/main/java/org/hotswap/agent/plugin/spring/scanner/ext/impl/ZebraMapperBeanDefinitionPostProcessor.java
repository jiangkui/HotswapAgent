package org.hotswap.agent.plugin.spring.scanner.ext.impl;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.scanner.ext.BeanDefinitionPostProcessor;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;

/**
 * 针对 Zebra Scanner 做的兼容。
 *
 * @author jiangkui
 * @since 1.5.0
 */
public class ZebraMapperBeanDefinitionPostProcessor implements BeanDefinitionPostProcessor {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ZebraMapperBeanDefinitionPostProcessor.class);

    public static final String ZEBRA_SCANNER = "com.dianping.zebra.dao.mybatis.ZebraClassPathMapperScanner";

    /**
     * Zebra Scanner 扫描 Mapper 文件生成 BeanDefinition 后，做的一些加工处理
     *
     * 详情参见 Zebra 源码：com.dianping.zebra.dao.mybatis.ZebraClassPathMapperScanner#doScan
     *
     * @param beanDefinitionHolder BeanDefinition
     * @param scanner 生成此 BeanDefinition 的扫描器
     */
    @Override
    public void postProcessAfterScanned(BeanDefinitionHolder beanDefinitionHolder, ClassPathBeanDefinitionScanner scanner) {
        if (scanner == null) {
            return;
        }
        if (!ZEBRA_SCANNER.equals(scanner.getClass().getName())) {
            return;
        }

        // 以下都是 Zebra 的源码，因为不是独立的方法，无法直接调用，只能截取对应的部分，原封不动抄过来。
        // 详情参见：com.dianping.zebra.dao.mybatis.ZebraClassPathMapperScanner#doScan
        try {
            GenericBeanDefinition definition = (GenericBeanDefinition) beanDefinitionHolder.getBeanDefinition();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Creating MapperFactoryBean with name '" + beanDefinitionHolder.getBeanName() + "' and '"
                        + definition.getBeanClassName() + "' mapperInterface");
            }

            // the mapper interface is the original class of the bean
            // but, the actual class of the bean is MapperFactoryBean
            definition.getConstructorArgumentValues().addGenericArgumentValue(definition.getBeanClassName());  //https://github.com/mybatis/spring/issues/58

            // 不想引入 Mybatis、Mybatis-Spring、Zebra 等相关 pom 依赖，所以使用反射的方式获取。
            definition.setBeanClass(scanner.getClass().getClassLoader().loadClass("com.dianping.zebra.dao.mybatis.ZebraMapperFactoryBean"));

            Object addToConfig = getValue(scanner, "addToConfig", Object.class);
            String sqlSessionFactoryBeanName = getValue(scanner, "sqlSessionFactoryBeanName", String.class);
            String sqlSessionTemplateBeanName = getValue(scanner, "sqlSessionTemplateBeanName", String.class);
            Object sqlSessionFactory = getValue(scanner, "sqlSessionFactory", Object.class);
            Object sqlSessionTemplate = getValue(scanner, "sqlSessionFactory", Object.class);

            definition.getPropertyValues().add("addToConfig", addToConfig);

            boolean explicitFactoryUsed = false;
            if (StringUtils.hasText(sqlSessionFactoryBeanName)) {
                definition.getPropertyValues().add("sqlSessionFactory",
                        new RuntimeBeanReference(sqlSessionFactoryBeanName));
                explicitFactoryUsed = true;
            } else if (sqlSessionFactory != null) {
                definition.getPropertyValues().add("sqlSessionFactory", sqlSessionFactory);
                explicitFactoryUsed = true;
            }

            if (StringUtils.hasText(sqlSessionTemplateBeanName)) {
                if (explicitFactoryUsed) {
                    LOGGER.warning("Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
                }
                definition.getPropertyValues().add("sqlSessionTemplate",
                        new RuntimeBeanReference(sqlSessionTemplateBeanName));
                explicitFactoryUsed = true;
            } else if (sqlSessionTemplate != null) {
                if (explicitFactoryUsed) {
                    LOGGER.warning("Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
                }
                definition.getPropertyValues().add("sqlSessionTemplate", sqlSessionTemplate);
                explicitFactoryUsed = true;
            }

            if (!explicitFactoryUsed) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Enabling autowire by type for MapperFactoryBean with name '" + beanDefinitionHolder.getBeanName()
                            + "'.");
                }
                definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            }

            LOGGER.info(ZEBRA_SCANNER + " postProcessAfterScanned success! beanName：" + beanDefinitionHolder.getBeanName());
        } catch (Exception e) {
            LOGGER.error(ZEBRA_SCANNER + " postProcessAfterScanned fail! beanName：" + beanDefinitionHolder.getBeanName(), e);
        }
    }

    private <T> T getValue(ClassPathBeanDefinitionScanner scanner, String fieldName, Class<T> t) throws Exception {
        try {
            Field field = scanner.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(scanner);
        } catch (Exception e) {
            LOGGER.error("获取 scanner 字段失败！", e.getMessage());
            throw e;
        }
    }
}
