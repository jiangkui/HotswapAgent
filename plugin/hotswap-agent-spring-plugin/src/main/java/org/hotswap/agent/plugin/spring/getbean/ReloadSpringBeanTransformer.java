/*
 * Copyright 2013-2019 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.getbean;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;

/**
 * reload the spring bean
 *
 * @author yulong.zhang
 *
 */
public class ReloadSpringBeanTransformer {
    public static final String DESTROY_SINGLETION = "destroySingleton";
    private static AgentLogger LOGGER = AgentLogger.getLogger(ReloadSpringBeanTransformer.class);

    /**
     *
     * @param clazz
     * @param classPool
     * @throws NotFoundException
     * @throws CannotCompileException
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.beans.factory.support.DefaultListableBeanFactory")
    public static void transform(CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod method = clazz.getDeclaredMethod(DESTROY_SINGLETION, new CtClass[]{classPool.get("java.lang.String")});
        method.insertAfter ( "org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent.registerReloadSpringBean($1);" );
        LOGGER.info ( "已经对DefaultListableBeanFactory.destroySingleton进行增强" );
    }
}
