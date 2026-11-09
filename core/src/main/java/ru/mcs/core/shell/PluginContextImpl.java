package ru.mcs.core.shell;

import java.util.function.Supplier;
import javax.swing.JComponent;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.ui.ToolWindowAnchor;

public final class PluginContextImpl implements PluginContext {

    private static final System.Logger LOG = System.getLogger("ru.mcs.plugin");

    private final String pluginId;
    private final UiHost host;
    private final ActionManager actions;

    public PluginContextImpl(String pluginId, UiHost host, ActionManager scopedActions) {
        this.pluginId = pluginId;
        this.host = host;
        this.actions = scopedActions;
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
    public ActionManager actions() {
        return actions;
    }

    @Override
    public void registerToolWindow(String id, String title, ToolWindowAnchor anchor,
                                   Supplier<JComponent> factory) {
        host.addToolWindow(pluginId, id, title, anchor, factory);
    }
}
