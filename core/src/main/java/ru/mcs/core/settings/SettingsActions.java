package ru.mcs.core.settings;

import javax.swing.JFrame;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.settings.SettingsManager;

public final class SettingsActions {

    public static final String ACTION_ID = "core.settings.open";

    private SettingsActions() {
    }

    public static void register(ActionManager actions, JFrame frame, SettingsManager settings, Runnable saveState) {
        actions.registerAction(ACTION_ID, new AnAction(Messages.get("settings.action"), null, Icons.lucide("settings")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                if (SettingsDialog.showDialog(frame, settings)) {
                    saveState.run();
                }
            }
        });
        actions.addToGroup(ActionIds.FILE_MENU, ACTION_ID, Constraints.before("core.exit"));
    }
}
