package ru.mcs.spi.swing.editor;

/** Сервис editor-плагина для регистрации renderers без compile-зависимости между JAR-плагинами. */
public interface BlockRendererExtensionRegistry {

    void register(BlockRendererExtension extension);

    void unregister(BlockRendererExtension extension);
}
