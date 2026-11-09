package ru.mcs.spi;

import java.util.function.Supplier;
import javax.swing.JComponent;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Единственный канал взаимодействия плагина с приложением (capability-модель). */
public interface PluginContext {

    String pluginId();

    void log(String message);

    /** Фаза 0: упрощённая регистрация. В Фазе 1 заменится на ActionManager. */
    void registerAction(String id, String text, Runnable handler);

    /** Фаза 0: упрощённая регистрация. В Фазе 1 заменится на ToolWindowManager. Компонент создаётся лениво. */
    void registerToolWindow(String id, String title, ToolWindowAnchor anchor, Supplier<JComponent> factory);
}
