package ru.mcs.core;

import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import ru.mcs.core.plugin.PluginManager;
import ru.mcs.core.shell.HeadlessHost;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.core.shell.ShellFrame;

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
        ShellFrame frame = new ShellFrame("Manuscriptium");
        PluginManager pm = new PluginManager(HomeDirs.pluginsDir(home),
                d -> new PluginContextImpl(d.id(), frame));
        frame.setVisible(true);
        try {
            pm.loadAll();
        } catch (Exception e) {
            frame.setStatus("Ошибка загрузки плагинов: " + e);
        }
        frame.setStatus("Плагины: " + pm.loadedIds()
                + (pm.failures().isEmpty() ? "" : "; ошибки: " + pm.failures()));
        Runtime.getRuntime().addShutdownHook(new Thread(pm::stopAll));
    }

    private static void runSmoke(Path home) {
        HeadlessHost host = new HeadlessHost();
        PluginManager pm = new PluginManager(HomeDirs.pluginsDir(home),
                d -> new PluginContextImpl(d.id(), host));
        int code = 1;
        try {
            pm.loadAll();
            System.out.println("MCS_SMOKE plugins=" + pm.loadedIds()
                    + " actions=" + host.actions()
                    + " toolWindows=" + host.toolWindows()
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
