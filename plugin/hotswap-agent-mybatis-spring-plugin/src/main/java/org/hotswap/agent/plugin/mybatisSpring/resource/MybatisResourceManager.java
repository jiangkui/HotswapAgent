package org.hotswap.agent.plugin.mybatisSpring.resource;

import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatisSpring.util.ReflectionUtils;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public class MybatisResourceManager {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MybatisResourceManager.class);

    private static Map<String, MybatisResource> xmlResourceMap = new ConcurrentHashMap<>();
    private static Map<String, MybatisResource> annotationResourceMap = new ConcurrentHashMap<>();

    public static void register(MybatisResource mybatisResource) {
        assert mybatisResource != null;
        AbstractMybatisResource abstractMybatisResource = (AbstractMybatisResource) mybatisResource;
        if (abstractMybatisResource.getType().equals(MybatisResourceEnum.XML)) {
            xmlResourceMap.put(abstractMybatisResource.getMatchPath(), abstractMybatisResource);
        } else {
            annotationResourceMap.put(abstractMybatisResource.getMatchPath(), abstractMybatisResource);
        }
    }

    /**
     * 初始化 mybatis 所有资源，用于过滤不相关的 url
     *
     * 时机：在变更后才触发初始化，可以确保 Configuration 已经完全加载完毕。
     *  - mapper xml 加载时机是：org.mybatis.spring.SqlSessionFactoryBean#afterPropertiesSet()，因为 MapperScannerConfigurer 需要用到此类，所以SqlSessionFactoryBean 实例化阶段实际是执行 BeanDefinitionRegistryPostProcessor 阶段实例化的，比较早。
     *  - annotation xml 加载时机是：org.mybatis.spring.mapper.MapperFactoryBean#getObject()，即，在 Spring getBean() 阶段处理的。
     */
    public static void init(List<Configuration> configurationList) {
        if (configurationList.isEmpty()) {
            return;
        }
        for (Configuration configuration : configurationList) {
            Set<String> loadedResources = (Set<String>) ReflectionUtils.getFieldValue(configuration, "loadedResources");
            if (loadedResources.isEmpty()) {
                continue;
            }
            for (String loadedResource : loadedResources) {
                try {
                    MybatisResource mybatisResource = MybatisResourceFactory.create(loadedResource, configuration);
                    register(mybatisResource);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * xml 路径格式有很多，不限于以下几种：
     * 1 = "interface org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     * 2 = "class path resource [org/hotswap/agent/plugin/mybatisSpring/Mapper.xml]"
     *                          还有这种路径 [file:///var/mappers/AuthorMapper.xml]"
     * @param url 变更的 url
     * @return 已加载的资源
     */
    public static MybatisResource findXmlResource(URL url) {
        if (url == null) {
            return null;
        }
        for (Map.Entry<String, MybatisResource> entry : xmlResourceMap.entrySet()) {
            // 用后缀匹配
            if (url.getPath().endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * annotation 路径只有一种：
     * - 0 = "namespace:org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     * @param ctClass class
     * @return 已加载的资源
     */
    public static MybatisResource findAnnotationResource(CtClass ctClass) {
        if (ctClass == null) {
            return null;
        }

        for (Map.Entry<String, MybatisResource> entry : annotationResourceMap.entrySet()) {
            // 用后缀匹配
            if (entry.getKey().endsWith(ctClass.getName())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
