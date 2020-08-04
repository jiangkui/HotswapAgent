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
package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.watch.WatchFileEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Do refresh Spring class (scanned by classpath scanner) based on URI or byte[] definition.
 *
 * This commands merges events of watcher.event(CREATE) and transformer hotswap reload to a single refresh command.
 */
public class ClassPathBeanRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanRefreshCommand.class);

    ClassLoader appClassLoader;

    String basePackage;

    String className;

    // either event or classDefinition is set by constructor (watcher or transformer)
    WatchFileEvent event;
    byte[] classDefinition;

    public ClassPathBeanRefreshCommand() {

    }

    public ClassPathBeanRefreshCommand(ClassLoader appClassLoader, String basePackage, String className, byte[] classDefinition) {
        this.appClassLoader = appClassLoader;
        this.basePackage = basePackage;
        this.className = className;
        this.classDefinition = classDefinition;
    }

    public ClassPathBeanRefreshCommand(ClassLoader appClassLoader, String basePackage, String className, WatchFileEvent event) {
        this.appClassLoader = appClassLoader;
        this.basePackage = basePackage;
        this.event = event;
        this.className = className;
    }

    @Override
    public void executeCommand() {
        if (isDeleteEvent()) {
            LOGGER.trace("Skip Spring reload for delete event on class '{}'", className);
            return;
        }

        try {
            if (classDefinition == null) {
                try {
                    this.classDefinition = IOUtils.toByteArray(event.getURI());
                } catch (IllegalArgumentException e) {
                    LOGGER.debug("File {} not found on filesystem (deleted?). Unable to refresh associated Spring bean.", event.getURI());
                    return;
                }
            }

            LOGGER.debug("Executing ClassPathBeanDefinitionScannerAgent.refreshClass('{}'), basePackage:{}", className, basePackage);

            Class<?> clazz = Class.forName("org.hotswap.agent.plugin.spring.scanner.ClassPathBeanDefinitionScannerAgent", true, appClassLoader);
            Method method  = clazz.getDeclaredMethod(
                    "refreshClass", new Class[] {String.class, byte[].class});
            method.invoke(null, basePackage, classDefinition);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing class {} in classLoader {}", e, className, appClassLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        }

    }

    /**
     * Check all merged events for delete and create events. If delete without create is found, than assume
     * file was deleted.
     */
    private boolean isDeleteEvent() {
        // for all merged commands including this command
        List<ClassPathBeanRefreshCommand> mergedCommands = new ArrayList<>();
        for (Command command : getMergedCommands()) {
            mergedCommands.add((ClassPathBeanRefreshCommand) command);
        }
        mergedCommands.add(this);

        boolean createFound = false;
        boolean deleteFound = false;
        for (ClassPathBeanRefreshCommand command : mergedCommands) {
            if (command.event != null) {
                if (command.event.getEventType().equals(FileEvent.DELETE))
                    deleteFound = true;
                if (command.event.getEventType().equals(FileEvent.CREATE))
                    createFound = true;
            }
        }

        LOGGER.trace("isDeleteEvent result {}: createFound={}, deleteFound={}", createFound, deleteFound);
        return !createFound && deleteFound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassPathBeanRefreshCommand that = (ClassPathBeanRefreshCommand) o;

        if (!appClassLoader.equals(that.appClassLoader)) return false;
        if (!className.equals(that.className)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        result = 31 * result + className.hashCode();
        /*
         * jiangkui 2020年08月04日20:09:32 add，解决：Mapper 热部署时，ZebraClassPathMapperScanner 无法执行的问题。（原因是多个 command hashCode 相同，被合并了）
         *
         * 问题：
         *      在 Zebra 场景中会有 bug，bug如下
         *          当一个 Mapper 修改后，热部署时，会产生多个 ClassPathBeanRefreshCommand，这些command仅主目录不同，如下：
         *          Command1 basePackage：com.sankuai.meituan.oasis.bdf
         *          Command2 basePackage：com.sankuai.meituan.oasis.bdf.dal.dao
         *
         * 原因：
         *      SpringPlugin.registerBasePackage() 时，会有多个 Scanner 注入，如：
         *          - Spring 主Scanner，扫描 com.sankuai.meituan.oasis.bdf 目录
         *          - Zebra Scanner，扫描 com.sankuai.meituan.oasis.bdf.dal.dao 目录
         *
         * 影响：
         *      如果被合并，会导致 Command2（ZebraClassPathMapperScanner） 无法执行，而 Mapper 变更时，需要由 Command2（ZebraClassPathMapperScanner）来获取 BeanDefinition，否则会获取不到对应的 BeanDefinition，最终热部署失败。
         */
        if (basePackage != null) {
            result = 31 * result + basePackage.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        return "ClassPathBeanRefreshCommand{" +
                "appClassLoader=" + appClassLoader +
                ", basePackage='" + basePackage + '\'' +
                ", className='" + className + '\'' +
                '}';
    }
}
