package org.hotswap.agent.plugin.mybatisSpring.resource;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.javassist.CtClass;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public class AnnotationResource extends AbstractMybatisResource {

    public AnnotationResource(String loadedResource, Configuration configuration) {
        this.type = MybatisResourceEnum.ANNOTATION;
        this.loadedResource = loadedResource;
        this.configuration = configuration;
        this.matchPath = loadedResource;
    }

    @Override
    public void reload(Object param) throws Exception {
        removeLoadedMark();
        reloadAnnotation((Class) param);
    }

    /**
     * 重新加载 Mapper（annotation 格式）
     *
     * @param redefiningClass 要加载的 Mapper
     */
    public void reloadAnnotation(Class redefiningClass) {
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(configuration, redefiningClass);
        parser.parse();
    }
}
