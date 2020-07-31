package org.hotswap.agent.plugin.mybatisSpring.resource;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public enum MybatisResourceEnum {

    XML("xml"), ANNOTATION("annotation");

    private String type;

    MybatisResourceEnum(String type) {
        this.type = type;
    }
}
