package ru.mcs.core.shell;

import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.editor.FileEditorManager;
import ru.mcs.spi.event.MessageBus;
import ru.mcs.spi.ui.NotificationManager;
import ru.mcs.spi.ui.StatusBar;
import ru.mcs.spi.ui.ToolWindowManager;

public final class PluginContextImpl implements PluginContext {

    private static final System.Logger LOG = System.getLogger("ru.mcs.plugin");

    private final String pluginId;
    private final ActionManager actions;
    private final ToolWindowManager toolWindows;
    private final FileEditorManager editors;
    private final StatusBar statusBar;
    private final NotificationManager notifications;
    private final MessageBus messageBus;

    public PluginContextImpl(String pluginId, ActionManager actions, ToolWindowManager toolWindows,
                             FileEditorManager editors, StatusBar statusBar, NotificationManager notifications,
                             MessageBus messageBus) {
        this.pluginId = pluginId;
        this.actions = actions;
        this.toolWindows = toolWindows;
        this.editors = editors;
        this.statusBar = statusBar;
        this.notifications = notifications;
        this.messageBus = messageBus;
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

    @Override
    public FileEditorManager editors() {
        return editors;
    }

    @Override
    public StatusBar statusBar() {
        return statusBar;
    }

    @Override
    public NotificationManager notifications() {
        return notifications;
    }

    @Override
    public MessageBus messageBus() {
        return messageBus;
    }
}
