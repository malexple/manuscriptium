package ru.mcs.spi.swing.settings;

import javax.swing.JComponent;
import ru.mcs.spi.settings.ConfigurationException;

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
