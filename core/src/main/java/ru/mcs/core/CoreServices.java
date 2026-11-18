package ru.mcs.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.action.DesktopMetadata;
import ru.mcs.core.bus.MessageBusImpl;
import ru.mcs.core.editor.EditorEventsBridge;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.editor.SessionStore;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.i18n.PluginMessages;
import ru.mcs.core.notification.NotificationManagerImpl;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.plugin.PluginState;
import ru.mcs.core.service.AtomicFileStoreImpl;
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
import ru.mcs.core.project.CoreProjectAccess;
import ru.mcs.core.project.ProjectIndexContributors;
import ru.mcs.core.project.ProjectPaths;
import ru.mcs.core.project.ProjectState;
import ru.mcs.spi.project.IndexContributorRegistry;
import ru.mcs.spi.project.ProjectAccess;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.BuiltInModule;
import ru.mcs.spi.editor.style.ShellThemeService;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.service.Scope;

/**
 * Корневой набор сервисов приложения (application-level). Состояние: {@code <home>/state/*.yml};
 * PROJECT-состояние — {@code <project>/.manuscriptium/state}. Старый {@code home/workspace/state} читается только
 * до первого открытия Project для совместимости с сессией предыдущих фаз.
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
    final DesktopMetadata metadata;
    final ToolWindowManagerImpl toolWindows;
    final FileEditorManagerImpl editors = new FileEditorManagerImpl();
    final CoreProjectAccess projectAccess = new CoreProjectAccess();
    final ProjectIndexContributors indexContributors = new ProjectIndexContributors();
    final MessageBusImpl bus = new MessageBusImpl();
    final StatusBarModel statusBar = new StatusBarModel();
    final NotificationManagerImpl notifications = new NotificationManagerImpl();
    final SessionStore sessionStore = new SessionStore();
    final WindowState windowState = new WindowState();
    final PluginState plugins = new PluginState();
    final ProjectState projectState = new ProjectState();
    final GeneralSettingsProvider generalSettings;
    private final List<BuiltInModule> builtIns = new ArrayList<>();
    private final Map<String, String> builtInFailures = new LinkedHashMap<>();

    CoreServices(Path home, boolean persist) {
        Path legacyProjectState = home.resolve("workspace").resolve("state");
        state = new StateManagerImpl(home.resolve("state"), legacyProjectState, persist);
        state.register("core", "core.project", Scope.APPLICATION, projectState, "core.vault");
        projectState.project().ifPresent(root -> state.changeProjectDirectory(ProjectPaths.stateDir(root)));
        state.register("core", "core.settings", Scope.APPLICATION, settings);
        Messages.init(settings.resolveLocale());
        themes = new McsThemeManager(home.resolve("themes"), settings.theme(), settings::setTheme);
        metadata = new DesktopMetadata(actions, ru.mcs.core.platform.MacOsSupport.isMacOs());
        generalSettings = new GeneralSettingsProvider(settings,
                metadata.settingsPage(GeneralSettingsProvider.ID));
        configurables.register(generalSettings);
        configurables.register(new AppearanceSettingsProvider(themes));
        services.register(AtomicFileStore.class, new AtomicFileStoreImpl());
        services.register(ShellThemeService.class, themes);
        services.register(ProjectAccess.class, projectAccess);
        services.register(IndexContributorRegistry.class, indexContributors);

        LayoutStore layout = new LayoutStore();
        state.register("core", "core.layout", Scope.APPLICATION, layout);
        state.register("core", "core.session", Scope.PROJECT, sessionStore);
        state.register("core", "core.window", Scope.APPLICATION, windowState);
        state.register("core", "core.plugins", Scope.APPLICATION, plugins);

        metadata.bind();
        toolWindows = new ToolWindowManagerImpl(actions, layout);
        CoreBootstrap.loadEditorProviders(editors);
        EditorEventsBridge.install(editors, bus);
    }

    void bindMetadata() {
        metadata.bind();
    }

    PluginManager newPluginManager(Path home) {
        return new PluginManager(List.of(HomeDirs.systemPluginsDir(), HomeDirs.pluginsDir(home)),
                this::contextFor, this::release, plugins::isEnabled);
    }

    /** Запускает явно скомпонованные модули продукта; внешние JAR этот список не пополняют. */
    void startBuiltIns(List<BuiltInModule> modules) {
        for (BuiltInModule module : modules) {
            String id = module.id();
            try {
                module.start(contextFor(module));
                builtIns.add(module);
            } catch (Throwable t) {
                builtInFailures.put(id, t.toString());
                LOG.log(System.Logger.Level.ERROR, "Built-in module failed: " + id, t);
                try {
                    module.stop();
                } catch (Throwable stopFailure) {
                    LOG.log(System.Logger.Level.WARNING, "Failed built-in module cleanup: " + id, stopFailure);
                }
                release(id);
            }
        }
    }

    List<String> builtInIds() {
        return builtIns.stream().map(BuiltInModule::id).toList();
    }

    Map<String, String> builtInFailures() {
        return Map.copyOf(builtInFailures);
    }

    void stopBuiltIns() {
        List<BuiltInModule> reversed = new ArrayList<>(builtIns);
        Collections.reverse(reversed);
        for (BuiltInModule module : reversed) {
            try {
                module.stop();
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.WARNING, "Built-in module stop failed: " + module.id(), t);
            }
            release(module.id());
        }
        builtIns.clear();
    }

    private PluginContext contextFor(BuiltInModule module) {
        String id = module.id();
        return new PluginContextImpl(id, actions.scopedFor(id), toolWindows.scopedFor(id), editors.scopedFor(id),
                statusBar.scopedFor(id), notifications.scopedFor(id), bus.scopedFor(id),
                services.scopedFor(id), state.scopedFor(id), configurables.scopedFor(id),
                PluginMessages.fromModule(module.getClass(), module.i18nBaseName(), Messages.locale()));
    }

    private PluginContext contextFor(PluginDescriptor d) {
        String id = d.id();
        return new PluginContextImpl(id, actions.scopedFor(id), toolWindows.scopedFor(id), editors.scopedFor(id),
                statusBar.scopedFor(id), notifications.scopedFor(id), bus.scopedFor(id),
                services.scopedFor(id), state.scopedFor(id), configurables.scopedFor(id),
                PluginMessages.create(d.directory(), d.i18n(), Messages.locale()));
    }

    /** Снять всё, что плагин зарегистрировал. Каждый шаг независим: сбой одного не мешает остальным. */
    private void release(PluginDescriptor d) {
        release(d.id());
    }

    private void release(String id) {
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
