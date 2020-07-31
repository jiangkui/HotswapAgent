package org.hotswap.agent.plugin.mybatisSpring.resource;

import org.apache.ibatis.session.Configuration;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public class MybatisResourceFactory {

    public static final String ANNOTATION_PREFIX = "interface";

    /**
     * configuration 内的 loadedResources 字段内容：
     * 0 = "namespace:org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     * 1 = "interface org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     * 2 = "class path resource [org/hotswap/agent/plugin/mybatisSpring/Mapper.xml]"
     *                          还有这种路径 [file:///var/mappers/AuthorMapper.xml]"
     *
     * @param loadedResource Configuration 内已加载的资源路径，有多种类型：
     *                      - XML 资源："class path resource [org/hotswap/agent/plugin/mybatisSpring/Mapper.xml]"
     *                      - Mapper 资源："interface org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     *                      - XML 内的命名空间资源："namespace:org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     *
     * @param configuration 持有此资源的 Configuration（未来会支持多数据源）
     * @return MybatisResource 抽象类
     */
    public static MybatisResource create(String loadedResource, Configuration configuration) {
        if (loadedResource == null) {
            throw new UnsupportedOperationException("loadedResource is null!");
        }

        if (loadedResource.trim().startsWith(ANNOTATION_PREFIX)) {
            return new AnnotationResource(loadedResource, configuration);
        } else {
            return new XmlResource(loadedResource, configuration);
        }
    }
}
