package ru.mcs.core.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Хост без UI: запоминает регистрации (smoke-тест в CI). Фабрики компонентов не вызываются. */
public final class HeadlessHost implements UiHost {

    private final List<String> toolWindows = new ArrayList<>();

    @Override
    public void addToolWindow(String pluginId, String id, String title, ToolWindowAnchor anchor,
                              Supplier<JComponent> factory) {
        toolWindows.add(id);
    }

    public List<String> toolWindows() {
        return List.copyOf(toolWindows);
    }
}
