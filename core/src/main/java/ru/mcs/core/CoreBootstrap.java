package ru.mcs.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.editor.EditorActions;
import ru.mcs.core.notification.NotificationLayer;
import ru.mcs.core.notification.NotificationsPanel;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.shell.ShellFrame;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.ui.NotificationType;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.ui.ToolWindowSpec;

/**
 * Запуск ядра. Аргументы: {@code --home=PATH} (каталог данных), {@code --smoke} (без UI и без записи на диск:
 * загрузить и активировать все плагины, напечатать итог, выйти с кодом 0/1 — для CI).
 */
public final class CoreBootstrap {

    private static final int AUTOSAVE_MS = 30_000;

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
        CoreServices s = new CoreServices(home, true);

        ShellFrame frame = new ShellFrame("Manuscriptium", s.actions, s.toolWindows, s.editors, s.statusBar,
                s.windowState);
        NotificationLayer.install(frame, s.notifications);
        EditorActions.register(s.actions, s.editors, frame);
        CoreActions.registerShellActions(s.actions, frame);
        s.toolWindows.register("core", ToolWindowSpec.of("core.notifications", "Notifications",
                ToolWindowAnchor.BOTTOM, () -> new NotificationsPanel(s.notifications)));

        PluginManager pm = s.newPluginManager(home);
        pm.setFailureListener((id, reason) ->
                s.notifications.notify("core", "Plugin failed: " + id, reason, NotificationType.ERROR));
        frame.setVisible(true);
        try {
            pm.loadAll();
        } catch (Exception e) {
            s.notifications.notify("core", "Plugins", "Plugin loading failed: " + e, NotificationType.ERROR);
        }

        // Вклады объявлены — провайдеры на месте (для onDemand-плагинов это заглушки): восстанавливаем вкладки.
        s.editors.restoreSession(s.sessionStore.loaded());
        s.sessionStore.activate(s.editors::currentSession);
        s.statusBar.setMessage("Plugins: " + pm.declaredIds() + ", active: " + pm.loadedIds());

        Timer autosave = new Timer(AUTOSAVE_MS, e -> s.state.saveChanged());
        autosave.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            s.state.saveChanged();
            pm.stopAll();
        }));
    }

    private static void runSmoke(Path home) {
        CoreServices s = new CoreServices(home, false);
        PluginManager pm = s.newPluginManager(home);
        int code = 1;
        try {
            pm.loadAll();
            List<String> lazy = new ArrayList<>(pm.declaredIds());
            lazy.removeAll(pm.loadedIds());
            pm.activateAll();
            System.out.println("MCS_SMOKE plugins=" + pm.loadedIds()
                    + " lazy=" + lazy
                    + " actions=" + s.actions.pluginActionIds()
                    + " tools=" + s.actions.childIds(ActionIds.TOOLS_MENU)
                    + " view=" + s.actions.childIds(ActionIds.VIEW_MENU)
                    + " toolbar=" + s.actions.childIds(ActionIds.MAIN_TOOLBAR)
                    + " toolWindows=" + s.toolWindows.ids()
                    + " editorProviders=" + s.editors.providerIds()
                    + " statusWidgets=" + s.statusBar.widgetIds()
                    + " state=" + s.state.ids()
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
