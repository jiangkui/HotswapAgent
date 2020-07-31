package org.hotswap.agent.plugin.mybatisSpring.util;

import java.lang.reflect.Field;

/**
 * @author jiangkui
 * @since 1.5.0
 */
public class ReflectionUtils {

    public static Object getFieldValue(Object target, String fieldName) {
        Field field = org.hotswap.agent.util.spring.util.ReflectionUtils.findField(target.getClass(), fieldName);
        assert field != null;
        org.hotswap.agent.util.spring.util.ReflectionUtils.makeAccessible(field);
        return org.hotswap.agent.util.spring.util.ReflectionUtils.getField(field, target);
    }

    public static void setFieldValue(Object target, String fieldName, Object value) {
        Field field = org.hotswap.agent.util.spring.util.ReflectionUtils.findField(target.getClass(), fieldName);
        assert field != null;
        org.hotswap.agent.util.spring.util.ReflectionUtils.makeAccessible(field);
        org.hotswap.agent.util.spring.util.ReflectionUtils.setField(field, target, value);
    }
}
