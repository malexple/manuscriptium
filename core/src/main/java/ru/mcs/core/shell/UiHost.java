package ru.mcs.core.shell;

import java.util.function.Supplier;
import javax.swing.JComponent;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Куда плагины «встраиваются». Фаза 0 — минимум; в Фазе 1 заменится на менеджеры оболочки. */
public interface UiHost {

    void addAction(String pluginId, String id, String text, Runnable handler);

    void addToolWindow(String pluginId, String id, String title, ToolWindowAnchor anchor,
                       Supplier<JComponent> factory);
}
