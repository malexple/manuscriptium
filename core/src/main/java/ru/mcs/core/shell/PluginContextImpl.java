package ru.mcs.core.shell;

import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.ui.ToolWindowManager;

public final class PluginContextImpl implements PluginContext {

    private static final System.Logger LOG = System.getLogger("ru.mcs.plugin");

    private final String pluginId;
    private final ActionManager actions;
    private final ToolWindowManager toolWindows;

    public PluginContextImpl(String pluginId, ActionManager scopedActions, ToolWindowManager scopedToolWindows) {
        this.pluginId = pluginId;
        this.actions = scopedActions;
        this.toolWindows = scopedToolWindows;
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
    public ToolWindowManager toolWindows() {
        return toolWindows;
    }
}
