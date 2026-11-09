package ru.mcs.core;

import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.core.shell.ShellFrame;
import ru.mcs.core.toolwindow.LayoutStore;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.action.ActionIds;

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

    private static PluginManager pluginManager(Path home, ActionManagerImpl actions, ToolWindowManagerImpl tw) {
        return new PluginManager(HomeDirs.pluginsDir(home),
                d -> new PluginContextImpl(d.id(), actions.scopedFor(d.id()), tw.scopedFor(d.id())),
                d -> {
                    tw.unregisterOwner(d.id());
                    actions.unregisterAll(d.id());
                });
    }

    private static void startUi(Path home) {
        LafInstaller.install();
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);
        ToolWindowManagerImpl toolWindows = new ToolWindowManagerImpl(actions, LayoutStore.at(home.resolve("layout.properties")));
        ShellFrame frame = new ShellFrame("Manuscriptium", actions, toolWindows);
        CoreActions.registerShellActions(actions, frame);

        PluginManager pm = pluginManager(home, actions, toolWindows);
        frame.setVisible(true);
        try {
            pm.loadAll();
        } catch (Exception e) {
            frame.setStatus("Ошибка загрузки плагинов: " + e);
        }
        frame.setStatus("Плагины: " + pm.loadedIds()
                + (pm.failures().isEmpty() ? "" : "; ошибки: " + pm.failures()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            toolWindows.save();
            pm.stopAll();
        }));
    }

    private static void runSmoke(Path home) {
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);
        ToolWindowManagerImpl toolWindows = new ToolWindowManagerImpl(actions, LayoutStore.inMemory());
        PluginManager pm = pluginManager(home, actions, toolWindows);
        int code = 1;
        try {
            pm.loadAll();
            System.out.println("MCS_SMOKE plugins=" + pm.loadedIds()
                    + " actions=" + actions.pluginActionIds()
                    + " tools=" + actions.childIds(ActionIds.TOOLS_MENU)
                    + " view=" + actions.childIds(ActionIds.VIEW_MENU)
                    + " toolbar=" + actions.childIds(ActionIds.MAIN_TOOLBAR)
                    + " toolWindows=" + toolWindows.ids()
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
