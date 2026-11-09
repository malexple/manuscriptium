package ru.mcs.spi;

/**
 * Точка входа плагина. Реализация объявляется в module-info плагина:
 * {@code provides ru.mcs.spi.Plugin with com.example.MyPlugin;}
 */
public interface Plugin {

    void start(PluginContext context) throws Exception;

    default void stop() {
    }
}
