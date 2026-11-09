package ru.mcs.core.shell;

import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.editor.FileEditorManager;
import ru.mcs.spi.event.MessageBus;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.ServiceRegistry;
import ru.mcs.spi.service.StateManager;
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
    private final ServiceRegistry services;
    private final StateManager state;
    private final I18n i18n;

    public PluginContextImpl(String pluginId, ActionManager actions, ToolWindowManager toolWindows,
                             FileEditorManager editors, StatusBar statusBar, NotificationManager notifications,
                             MessageBus messageBus, ServiceRegistry services, StateManager state, I18n i18n) {
        this.pluginId = pluginId;
        this.actions = actions;
        this.toolWindows = toolWindows;
        this.editors = editors;
        this.statusBar = statusBar;
        this.notifications = notifications;
        this.messageBus = messageBus;
        this.services = services;
        this.state = state;
        this.i18n = i18n;
    }

    /** Без локализации (ключи возвращаются как есть) — для тестов. */
    public PluginContextImpl(String pluginId, ActionManager actions, ToolWindowManager toolWindows,
                             FileEditorManager editors, StatusBar statusBar, NotificationManager notifications,
                             MessageBus messageBus, ServiceRegistry services, StateManager state) {
        this(pluginId, actions, toolWindows, editors, statusBar, notifications, messageBus, services, state,
                I18n.passthrough());
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

    @Override
    public ServiceRegistry services() {
        return services;
    }

    @Override
    public StateManager state() {
        return state;
    }

    @Override
    public I18n i18n() {
        return i18n;
    }
}
