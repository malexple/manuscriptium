package ru.mcs.spi.settings;

import javax.swing.JComponent;

/** Одна открытая страница настроек. Все методы вызываются на EDT. */
public interface Configurable {

    JComponent createComponent();

    boolean isModified();

    void apply() throws ConfigurationException;

    /** Вернуть значения редактируемой страницы к состоянию модели. */
    default void reset() {
    }

    /** Освободить ресурсы Swing-компонента при закрытии диалога. */
    default void disposeUIResources() {
    }
}
