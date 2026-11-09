package ru.mcs.core.shell;

import java.util.function.Supplier;
import javax.swing.JComponent;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.ui.ToolWindowAnchor;

public final class PluginContextImpl implements PluginContext {

    private static final System.Logger LOG = System.getLogger("ru.mcs.plugin");

    private final String pluginId;
    private final UiHost host;

    public PluginContextImpl(String pluginId, UiHost host) {
        this.pluginId = pluginId;
        this.host = host;
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public void log(String message) {
        LOG.log(System.Logger.Level.INFO, "[" + pluginId + "] " + message);
    }

    @Override
    public void registerAction(String id, String text, Runnable handler) {
        host.addAction(pluginId, id, text, handler);
    }

    @Override
    public void registerToolWindow(String id, String title, ToolWindowAnchor anchor,
                                   Supplier<JComponent> factory) {
        host.addToolWindow(pluginId, id, title, anchor, factory);
    }
}
