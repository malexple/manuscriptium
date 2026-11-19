package ru.mcs.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.editor.EditorActions;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;
import ru.mcs.core.notification.NotificationLayer;
import ru.mcs.core.notification.NotificationsPanel;
import ru.mcs.core.platform.MacOsSupport;
import ru.mcs.core.plugin.PluginInstaller;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.plugin.PluginsConfigurableProvider;
import ru.mcs.core.settings.SettingsActions;
import ru.mcs.core.shell.ShellFrame;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.swing.BuiltInModule;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.ui.NotificationType;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.swing.ui.ToolWindowSpec;
import ru.mcs.core.project.ProjectController;
import ru.mcs.core.action.CommandPalette;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.shell.ShellDataContext;
import ru.mcs.spi.swing.editor.FileEditorManager;
import ru.mcs.spi.swing.editor.FileEditorProvider;

/**
 * Запуск ядра. Аргументы: {@code --home=PATH} (каталог данных), {@code --smoke} (без UI и без записи на диск:
 * загрузить и активировать все плагины, напечатать итог, выйти с кодом 0/1 — для CI). Необязательный
 * {@code --smoke-actions=id1,id2} дополнительно выполняет указанные действия и проверяет их доступность.
 */
public final class CoreBootstrap {

    private static final int AUTOSAVE_MS = 30_000;
    private static final System.Logger LOG = System.getLogger(CoreBootstrap.class.getName());

    private CoreBootstrap() {
    }

    public static void start(String[] args, BuiltInModule... builtIns) {
        Path home = HomeDirs.resolve(argValue(args, "--home="));
        try {
            new PluginInstaller(HomeDirs.pluginsDir(home)).applyPending();
        } catch (IOException | RuntimeException e) {
            // Старая версия остаётся на месте благодаря rollback; приложение может продолжить запуск.
            LOG.log(System.Logger.Level.ERROR, "Cannot apply pending plugin update", e);
        }
        if (Arrays.asList(args).contains("--smoke")) {
            runSmoke(home, args, builtIns);
        } else {
            SwingUtilities.invokeLater(() -> startUi(home, args, builtIns));
        }
    }

    /**
     * Создаёт редакторские сервисы без UI и Project. Провайдеры приходят только из текущего ModuleLayer/classpath;
     * состав продукта определяет набор доступных форматов.
     */
    public static FileEditorManager createHeadlessEditorManager() {
        return loadEditorProviders(new FileEditorManagerImpl());
    }

    static FileEditorManagerImpl loadEditorProviders(FileEditorManagerImpl editors) {
        for (FileEditorProvider provider : ServiceLoader.load(FileEditorProvider.class)) {
            editors.registerProvider(provider);
        }
        return editors;
    }

    private static void startUi(Path home, String[] args, BuiltInModule... builtIns) {
        LafInstaller.install();
        CoreServices s = new CoreServices(home, true);
        s.startBuiltIns(List.of(builtIns));
        s.bindMetadata();
        s.themes.start();

        ShellFrame frame = new ShellFrame("Manuscriptium", s.actions, s.toolWindows, s.editors, s.statusBar,
                s.windowState);
        NotificationLayer.install(frame, s.notifications);
        EditorActions.register(s.actions, s.editors, frame);
        s.bindMetadata();
        CoreActions.registerShellActions(s.actions, frame);
        s.bindMetadata();
        CommandPalette.install(
                frame,
                s.actions,
                () -> new ShellDataContext(s.editors),
                s.metadata.keyStroke(CommandPalette.ACTION_ID)
        );
        s.bindMetadata();
        PluginManager pm = s.newPluginManager(home);
        PluginRestartController restarter = new PluginRestartController(frame, args);
        s.generalSettings.onRestartRequired(restarter::markRequired);
        s.configurables.register(new PluginsConfigurableProvider(pm, s.plugins,
                new PluginInstaller(HomeDirs.pluginsDir(home)), restarter::markRequired));
        SettingsActions.register(s.actions, frame, s.configurables, s.state::saveChanged, restarter::promptIfNeeded);
        s.bindMetadata();
        MacOsSupport.installApplicationMenu(
                () -> CoreActions.showAbout(frame),
                () -> SettingsActions.open(frame, s.configurables, s.state::saveChanged, restarter::promptIfNeeded),
                () -> CoreActions.requestQuit(frame)
        );
        s.toolWindows.register("core", ToolWindowSpec.of("core.notifications", Messages.get("notifications.title"),
                        ToolWindowAnchor.BOTTOM, () -> new NotificationsPanel(s.notifications))
                .withIcon(Icons.lucide("bell")));
        ProjectController projectController = new ProjectController(frame, s.actions, s.toolWindows, s.editors,
                s.sessionStore, s.state, s.projectState, s.statusBar, s.notifications, s.projectAccess, s.indexContributors, s.services);
        s.bindMetadata();

        pm.setFailureListener((id, reason) -> s.notifications.notify("core",
                Messages.get("plugin.failed.title", id), reason, NotificationType.ERROR));
        frame.setVisible(true);
        try {
            pm.loadAll();
        } catch (Exception e) {
            s.notifications.notify("core", Messages.get("plugins.loading.failed.title"),
                    Messages.get("plugins.loading.failed", e.toString()), NotificationType.ERROR);
        }

        // Вклады объявлены — провайдеры на месте: можно открыть Project и восстановить его вкладки.
        s.statusBar.setMessage(Messages.get("plugins.status", pm.declaredIds(), pm.loadedIds()));
        projectController.openInitial();

        Timer autosave = new Timer(AUTOSAVE_MS, e -> s.state.saveChanged());
        autosave.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            s.themes.close();
            s.state.saveChanged();
            projectController.close();
            pm.stopAll();
            s.stopBuiltIns();
        }));
    }

    private static void runSmoke(Path home, String[] args, BuiltInModule... builtIns) {
        CoreServices s = new CoreServices(home, false);
        s.startBuiltIns(List.of(builtIns));
        s.bindMetadata();
        PluginManager pm = s.newPluginManager(home);
        int code = 1;
        try {
            pm.loadAll();
            List<String> lazy = new ArrayList<>(pm.declaredIds());
            lazy.removeAll(pm.loadedIds());
            pm.activateAll();
            List<String> smokeActions = runSmokeActions(s, argValue(args, "--smoke-actions="));
            System.out.println("MCS_SMOKE builtIns=" + s.builtInIds()
                    + " plugins=" + pm.loadedIds()
                    + " lazy=" + lazy
                    + " smokeActions=" + smokeActions
                    + " locale=" + Messages.locale()
                    + " actions=" + s.actions.pluginActionIds()
                    + " tools=" + s.actions.childIds(ActionIds.TOOLS_MENU)
                    + " view=" + s.actions.childIds(ActionIds.VIEW_MENU)
                    + " toolbar=" + s.actions.childIds(ActionIds.MAIN_TOOLBAR)
                    + " toolWindows=" + s.toolWindows.ids()
                    + " editorProviders=" + s.editors.providerIds()
                    + " settings=" + s.configurables.getProviders().stream()
                            .map(p -> p.getId()).toList()
                    + " statusWidgets=" + s.statusBar.widgetIds()
                    + " state=" + s.state.ids()
                    + " failures=" + pm.failures()
                    + " builtInFailures=" + s.builtInFailures());
            code = pm.failures().isEmpty() && s.builtInFailures().isEmpty() && !s.builtInIds().isEmpty() ? 0 : 1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        pm.stopAll();
        s.stopBuiltIns();
        System.exit(code);
    }

    private static List<String> runSmokeActions(CoreServices services, String argument) {
        if (argument == null || argument.isBlank()) {
            return List.of();
        }
        List<String> ids = Arrays.stream(argument.split(",")).map(String::trim).filter(id -> !id.isEmpty()).toList();
        for (String id : ids) {
            AnAction action = services.actions.getAction(id);
            if (action == null) {
                throw new IllegalStateException("Smoke action is not registered: " + id);
            }
            AnActionEvent event = new AnActionEvent(action.getTemplatePresentation().copy(),
                    ActionPlaces.MAIN_MENU, DataContext.EMPTY);
            action.update(event);
            if (!event.getPresentation().isEnabled()) {
                throw new IllegalStateException("Smoke action is disabled: " + id);
            }
            action.actionPerformed(event);
        }
        return ids;
    }

    private static String argValue(String[] args, String prefix) {
        for (String a : args) {
            if (a.startsWith(prefix)) {
                return a.substring(prefix.length());
            }
        }
        return null;
    }
}
