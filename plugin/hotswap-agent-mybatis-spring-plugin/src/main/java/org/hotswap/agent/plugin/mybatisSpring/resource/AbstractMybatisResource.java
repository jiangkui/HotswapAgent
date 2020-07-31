package org.hotswap.agent.plugin.mybatisSpring.resource;

import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public abstract class AbstractMybatisResource implements MybatisResource {

    protected MybatisResourceEnum type;

    /**
     * 经过加工，用来匹配是否为 mybatis 资源
     */
    protected String matchPath;

    /**
     * configuration 内的 loadedResources 字段内容：
     * 0 = "namespace:org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     * 1 = "interface org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     * 2 = "class path resource [org/hotswap/agent/plugin/mybatisSpring/Mapper.xml]"
     *                          还有这种路径 [file:///var/mappers/AuthorMapper.xml]"
     */
    protected String loadedResource;

    protected Configuration configuration;

    /**
     * 删掉 resource 已加载的标记
     *
     * 报错：
     *     HOTSWAP AGENT: 16:30:25.589 ERROR (org.hotswap.agent.plugin.mybatisSpring.MybatisSpringPlugin) - loadedResources
     *     java.lang.NoSuchFieldException: loadedResources
     * 原因：
     *     是被 mybatis plugin 代理了，所以拿不到 field。暂时取消 mybatis plugin。
     */
    public void removeLoadedMark() throws Exception {
        Field loadedResourcesField = configuration.getClass().getDeclaredField("loadedResources");
        loadedResourcesField.setAccessible(true);
        Set loadedResourcesSet = ((Set) loadedResourcesField.get(configuration));
        loadedResourcesSet.remove(loadedResource);
    }

    public MybatisResourceEnum getType() {
        return type;
    }

    public void setType(MybatisResourceEnum type) {
        this.type = type;
    }

    public String getLoadedResource() {
        return loadedResource;
    }

    public void setLoadedResource(String loadedResource) {
        this.loadedResource = loadedResource;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public String getMatchPath() {
        return matchPath;
    }

    public void setMatchPath(String matchPath) {
        this.matchPath = matchPath;
    }
}
