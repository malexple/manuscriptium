package ru.mcs.core.settings;

import javax.swing.JFrame;
import ru.mcs.core.platform.MacOsSupport;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.swing.settings.SettingsManager;

public final class SettingsActions {

    public static final String ACTION_ID = "core.settings.open";

    private SettingsActions() {
    }

    public static void register(ActionManager actions, JFrame frame, SettingsManager settings, Runnable saveState) {
        register(actions, frame, settings, saveState, () -> { });
    }

    public static void register(ActionManager actions, JFrame frame, SettingsManager settings, Runnable saveState,
                                Runnable afterClose) {
        register(actions, frame, settings, saveState, MacOsSupport.isMacOs(), afterClose);
    }

    static void register(ActionManager actions, JFrame frame, SettingsManager settings, Runnable saveState,
                         boolean macOs) {
        register(actions, frame, settings, saveState, macOs, () -> { });
    }

    static void register(ActionManager actions, JFrame frame, SettingsManager settings, Runnable saveState,
                         boolean macOs, Runnable afterClose) {
        actions.registerAction(ACTION_ID, new AnAction(Messages.get("settings.action"), null, Icons.lucide("settings")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                open(frame, settings, saveState, afterClose);
            }
        });
        // DesktopMetadata добавляет действие в File только вне macOS.
    }

    public static void open(JFrame frame, SettingsManager settings, Runnable saveState) {
        open(frame, settings, saveState, () -> { });
    }

    public static void open(JFrame frame, SettingsManager settings, Runnable saveState, Runnable afterClose) {
        if (SettingsDialog.showDialog(frame, settings)) {
            saveState.run();
        }
        afterClose.run();
    }
}
