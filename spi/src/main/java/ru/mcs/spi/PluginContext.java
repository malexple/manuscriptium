package ru.mcs.spi;

import java.util.function.Supplier;
import javax.swing.JComponent;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Единственный канал взаимодействия плагина с приложением (capability-модель). */
public interface PluginContext {

    String pluginId();

    void log(String message);

    /**
     * Менеджер действий, привязанный к плагину: всё, что плагин зарегистрировал,
     * автоматически снимается при остановке плагина.
     */
    ActionManager actions();

    /** Фаза 1 (срез 1): временная регистрация. В срезе 2 заменится на ToolWindowManager. */
    void registerToolWindow(String id, String title, ToolWindowAnchor anchor, Supplier<JComponent> factory);
}
