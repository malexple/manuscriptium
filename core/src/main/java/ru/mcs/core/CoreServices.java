package ru.mcs.core;

import java.nio.file.Path;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.bus.MessageBusImpl;
import ru.mcs.core.editor.EditorEventsBridge;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.editor.SessionStore;
import ru.mcs.core.editor.TextFileEditorProvider;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.i18n.PluginMessages;
import ru.mcs.core.notification.NotificationManagerImpl;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.service.ServiceRegistryImpl;
import ru.mcs.core.settings.AppSettings;
import ru.mcs.core.settings.AppearanceSettingsProvider;
import ru.mcs.core.settings.GeneralSettingsProvider;
import ru.mcs.core.settings.SettingsManagerImpl;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.core.shell.WindowState;
import ru.mcs.core.state.StateManagerImpl;
import ru.mcs.core.statusbar.StatusBarModel;
import ru.mcs.core.theme.McsThemeManager;
import ru.mcs.core.toolwindow.LayoutStore;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.service.Scope;

/**
 * Корневой набор сервисов приложения (application-level). Состояние: {@code <home>/state/*.yml};
 * VAULT-состояние пока временно в {@code <home>/workspace/state} (в Фазе 4 переедет в vault).
 * Язык интерфейса читается из настроек до создания любых UI-сервисов и дальше не меняется.
 */
final class CoreServices {

    private static final System.Logger LOG = System.getLogger(CoreServices.class.getName());

    final StateManagerImpl state;
    final AppSettings settings = new AppSettings();
    final McsThemeManager themes;
    final SettingsManagerImpl configurables = new SettingsManagerImpl();
    final ServiceRegistryImpl services = new ServiceRegistryImpl();
    final ActionManagerImpl actions = new ActionManagerImpl();
    final ToolWindowManagerImpl toolWindows;
    final FileEditorManagerImpl editors = new FileEditorManagerImpl();
    final MessageBusImpl bus = new MessageBusImpl();
    final StatusBarModel statusBar = new StatusBarModel();
    final NotificationManagerImpl notifications = new NotificationManagerImpl();
    final SessionStore sessionStore = new SessionStore();
    final WindowState windowState = new WindowState();

    CoreServices(Path home, boolean persist) {
        state = new StateManagerImpl(home.resolve("state"), home.resolve("workspace").resolve("state"), persist);
        state.register("core", "core.settings", Scope.APPLICATION, settings);
        Messages.init(settings.resolveLocale());
        themes = new McsThemeManager(settings, home.resolve("themes"));
        configurables.register(new GeneralSettingsProvider(settings));
        configurables.register(new AppearanceSettingsProvider(themes));

        LayoutStore layout = new LayoutStore();
        state.register("core", "core.layout", Scope.APPLICATION, layout);
        state.register("core", "core.session", Scope.APPLICATION, sessionStore);
        state.register("core", "core.window", Scope.APPLICATION, windowState);

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
                statusBar.scopedFor(id), notifications.scopedFor(id), bus.scopedFor(id),
                services.scopedFor(id), state.scopedFor(id), configurables.scopedFor(id),
                PluginMessages.create(d, Messages.locale()));
    }

    /** Снять всё, что плагин зарегистрировал. Каждый шаг независим: сбой одного не мешает остальным. */
    private void release(PluginDescriptor d) {
        String id = d.id();
        safely(id, () -> state.unregisterOwner(id));
        safely(id, () -> editors.unregisterOwner(id));
        safely(id, () -> toolWindows.unregisterOwner(id));
        safely(id, () -> statusBar.removeOwner(id));
        safely(id, () -> bus.unsubscribeOwner(id));
        safely(id, () -> services.unregisterOwner(id));
        safely(id, () -> actions.unregisterAll(id));
        safely(id, () -> configurables.unregisterAll(id));
    }

    private static void safely(String pluginId, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "Cleanup step failed for " + pluginId, t);
        }
    }
}
