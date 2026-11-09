package ru.mcs.core;

import java.nio.file.Path;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.bus.MessageBusImpl;
import ru.mcs.core.editor.EditorEventsBridge;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.editor.TextFileEditorProvider;
import ru.mcs.core.notification.NotificationManagerImpl;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.core.statusbar.StatusBarModel;
import ru.mcs.core.toolwindow.LayoutStore;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.PluginContext;

/** Корневой набор сервисов приложения (application-level). Позже станет ServiceRegistry. */
final class CoreServices {

    private static final System.Logger LOG = System.getLogger(CoreServices.class.getName());

    final ActionManagerImpl actions = new ActionManagerImpl();
    final ToolWindowManagerImpl toolWindows;
    final FileEditorManagerImpl editors = new FileEditorManagerImpl();
    final MessageBusImpl bus = new MessageBusImpl();
    final StatusBarModel statusBar = new StatusBarModel();
    final NotificationManagerImpl notifications = new NotificationManagerImpl();

    CoreServices(LayoutStore layout) {
        CoreActions.registerStandardGroups(actions);
        toolWindows = new ToolWindowManagerImpl(actions, layout);
        editors.registerProvider(new TextFileEditorProvider());
        EditorEventsBridge.install(editors, bus);
    }

    PluginManager newPluginManager(Path home) {
        return new PluginManager(HomeDirs.pluginsDir(home), this::contextFor, this::release);
    }

    private PluginContext contextFor(PluginDescriptor d) {
        String id = d.id();
        return new PluginContextImpl(id, actions.scopedFor(id), toolWindows.scopedFor(id), editors.scopedFor(id),
                statusBar.scopedFor(id), notifications.scopedFor(id), bus.scopedFor(id));
    }

    /** Снять всё, что плагин зарегистрировал. Каждый шаг независим: сбой одного не мешает остальным. */
    private void release(PluginDescriptor d) {
        String id = d.id();
        safely(id, () -> editors.unregisterOwner(id));
        safely(id, () -> toolWindows.unregisterOwner(id));
        safely(id, () -> statusBar.removeOwner(id));
        safely(id, () -> bus.unsubscribeOwner(id));
        safely(id, () -> actions.unregisterAll(id));
    }

    private static void safely(String pluginId, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "Cleanup step failed for " + pluginId, t);
        }
    }
}
