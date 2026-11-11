package ru.mcs.core.action;

import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import ru.mcs.core.MacOsSupport;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;
import ru.mcs.spi.McsSdk;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DefaultActionGroup;

/** Стандартные группы и встроенные действия ядра. Тексты — из {@link Messages} (язык фиксируется при запуске). */
public final class CoreActions {

    private CoreActions() {
    }

    public static void registerStandardGroups(ActionManager am) {
        am.registerAction(ActionIds.MAIN_MENU, new DefaultActionGroup("", false));
        menu(am, ActionIds.FILE_MENU, Messages.get("menu.file"));
        menu(am, ActionIds.VIEW_MENU, Messages.get("menu.view"));
        menu(am, ActionIds.TOOLS_MENU, Messages.get("menu.tools"));
        menu(am, ActionIds.HELP_MENU, Messages.get("menu.help"));
        am.registerAction(ActionIds.MAIN_TOOLBAR, new DefaultActionGroup("", false));
        am.registerAction(ActionIds.EDITOR_TAB_POPUP, new DefaultActionGroup("", false));
    }

    /** Exit и About; регистрировать после действий File, чтобы Exit оказался последним. */
    public static void registerShellActions(ActionManager am, JFrame frame) {
        registerShellActions(am, frame, MacOsSupport.isMacOs());
    }

    static void registerShellActions(ActionManager am, JFrame frame, boolean macOs) {
        am.registerAction("core.exit", new AnAction(Messages.get("action.exit"), null, Icons.lucide("log-out")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                requestQuit(frame);
            }
        });
        am.registerAction("core.about", new AnAction(Messages.get("action.about"), null, Icons.lucide("circle-question-mark")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                showAbout(frame);
            }
        });

        // На macOS эти команды уже находятся в нативном меню приложения.
        // Сами действия остаются зарегистрированными и доступными в палитре команд.
        if (!macOs) {
            am.addToGroup(ActionIds.FILE_MENU, "core.exit", Constraints.LAST);
            am.addToGroup(ActionIds.HELP_MENU, "core.about", Constraints.LAST);
        }
    }

    public static void showAbout(JFrame frame) {
        JOptionPane.showMessageDialog(frame, Messages.get("about.text", McsSdk.VERSION));
    }

    public static void requestQuit(JFrame frame) {
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }

    private static void menu(ActionManager am, String id, String text) {
        am.registerAction(id, new DefaultActionGroup(text, true));
        am.addToGroup(ActionIds.MAIN_MENU, id, Constraints.LAST);
    }
}
