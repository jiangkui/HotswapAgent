package org.hotswap.agent.plugin.mybatisSpring.resource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatisSpring.MybatisSpringPlugin;

import java.net.URL;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public class XmlResource extends AbstractMybatisResource {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlResource.class);

    public XmlResource(String loadedResource, Configuration configuration) {
        this.type = MybatisResourceEnum.XML;
        this.loadedResource = loadedResource;
        this.configuration = configuration;
        this.matchPath = parseMatchPath(loadedResource);
    }

    public static final String NAMESPACE = "namespace";

    /**
     * xml 路径格式比较多，有以下几种，可能还有其他的：
     * 0 = "namespace:org.hotswap.agent.plugin.mybatisSpring.domain.UserMapper"
     * 2 = "class path resource [org/hotswap/agent/plugin/mybatisSpring/Mapper.xml]"
     *            还有这种路径 [file:///var/mappers/AuthorMapper.xml]"
     */
    private String parseMatchPath(String loadedResource) {
        if (loadedResource.startsWith(NAMESPACE)) {
            return loadedResource;
        }
        if (loadedResource.contains("[") && loadedResource.contains("]")) {
            return loadedResource.substring(loadedResource.indexOf("[") + 1, loadedResource.indexOf("]"));
        }
        LOGGER.error("不支持并且没处理的路径！此路径不支持热部署！path：" + loadedResource);
        return loadedResource;
    }

    @Override
    public void reload(Object param) throws Exception {
        removeLoadedMark();
        reloadXML((URL) param);
    }

    /**
     * 重新加载 xml
     *
     * @param url 要 reload 的xml
     */
    public void reloadXML(URL url) throws Exception {
        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(url.openConnection().getInputStream(), configuration, loadedResource, configuration.getSqlFragments());
        xmlMapperBuilder.parse();
    }
}
