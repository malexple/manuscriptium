package ru.mcs.core.shell;

import java.util.function.Supplier;
import javax.swing.JComponent;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Куда плагины «встраиваются». Действия теперь идут через ActionManager; панели — до среза 2. */
public interface UiHost {

    void addToolWindow(String pluginId, String id, String title, ToolWindowAnchor anchor,
                       Supplier<JComponent> factory);
}
