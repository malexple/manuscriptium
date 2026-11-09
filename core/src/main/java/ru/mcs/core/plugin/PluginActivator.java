package ru.mcs.core.plugin;

import ru.mcs.spi.Plugin;

/** Загружает плагин (если ещё не загружен) и возвращает его экземпляр. */
@FunctionalInterface
public interface PluginActivator {

    Plugin activate(String pluginId) throws Exception;
}
