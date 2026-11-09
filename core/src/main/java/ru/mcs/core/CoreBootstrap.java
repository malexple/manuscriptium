package ru.mcs.core;

import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.editor.EditorActions;
import ru.mcs.core.editor.SessionStore;
import ru.mcs.core.notification.NotificationLayer;
import ru.mcs.core.notification.NotificationsPanel;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.shell.ShellFrame;
import ru.mcs.core.toolwindow.LayoutStore;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.ui.NotificationType;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.ui.ToolWindowSpec;

/**
 * Запуск ядра. Аргументы: {@code --home=PATH} (каталог данных), {@code --smoke} (без UI:
 * загрузить плагины, напечатать итог, выйти с кодом 0/1 — для CI).
 */
public final class CoreBootstrap {

    private CoreBootstrap() {
    }

    public static void start(String[] args) {
        Path home = HomeDirs.resolve(argValue(args, "--home="));
        if (Arrays.asList(args).contains("--smoke")) {
            runSmoke(home);
        } else {
            SwingUtilities.invokeLater(() -> startUi(home));
        }
    }

    private static void startUi(Path home) {
        LafInstaller.install();
        CoreServices s = new CoreServices(LayoutStore.at(home.resolve("layout.properties")));
        SessionStore session = SessionStore.at(home.resolve("session.properties"));

        ShellFrame frame = new ShellFrame("Manuscriptium", s.actions, s.toolWindows, s.editors, s.statusBar);
        NotificationLayer.install(frame, s.notifications);
        EditorActions.register(s.actions, s.editors, frame);
        CoreActions.registerShellActions(s.actions, frame);
        s.toolWindows.register("core", ToolWindowSpec.of("core.notifications", "Notifications",
                ToolWindowAnchor.BOTTOM, () -> new NotificationsPanel(s.notifications)));

        PluginManager pm = s.newPluginManager(home);
        frame.setVisible(true);
        try {
            pm.loadAll();
        } catch (Exception e) {
            s.notifications.notify("core", "Plugins", "Plugin loading failed: " + e, NotificationType.ERROR);
        }
        pm.failures().forEach((id, reason) ->
                s.notifications.notify("core", "Plugin failed: " + id, reason, NotificationType.ERROR));
        s.editors.restoreSession(session);
        s.statusBar.setMessage("Plugins: " + pm.loadedIds());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            s.editors.saveSession(session);
            s.toolWindows.save();
            pm.stopAll();
        }));
    }

    private static void runSmoke(Path home) {
        CoreServices s = new CoreServices(LayoutStore.inMemory());
        PluginManager pm = s.newPluginManager(home);
        int code = 1;
        try {
            pm.loadAll();
            System.out.println("MCS_SMOKE plugins=" + pm.loadedIds()
                    + " actions=" + s.actions.pluginActionIds()
                    + " tools=" + s.actions.childIds(ActionIds.TOOLS_MENU)
                    + " view=" + s.actions.childIds(ActionIds.VIEW_MENU)
                    + " toolbar=" + s.actions.childIds(ActionIds.MAIN_TOOLBAR)
                    + " toolWindows=" + s.toolWindows.ids()
                    + " editorProviders=" + s.editors.providerIds()
                    + " statusWidgets=" + s.statusBar.widgetIds()
                    + " failures=" + pm.failures());
            code = pm.failures().isEmpty() && !pm.loadedIds().isEmpty() ? 0 : 1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        pm.stopAll();
        System.exit(code);
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
