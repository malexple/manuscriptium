package ru.mcs.spi.settings;

/**
 * Метаданные страницы доступны без создания UI. Реализация может активировать плагин
 * только в {@link #createConfigurable()} — при первом открытии своей страницы.
 */
public interface ConfigurableProvider {

    String getId();

    String getDisplayName();

    /** ID родительской страницы или null для корневой. */
    default String getParentId() {
        return null;
    }

    Configurable createConfigurable();
}
