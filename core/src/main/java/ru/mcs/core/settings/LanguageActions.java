package ru.mcs.core.settings;

import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DefaultActionGroup;

/** Временный выбор языка в меню View → Language (до диалога настроек). Применяется после перезапуска. */
public final class LanguageActions {

    private LanguageActions() {
    }

    public static void register(ActionManager am, JFrame frame, AppSettings settings) {
        am.registerAction("core.language", new DefaultActionGroup(Messages.get("menu.language"), true));
        for (String lang : List.of(AppSettings.AUTO, AppSettings.EN, AppSettings.RU)) {
            String id = "core.language." + lang;
            am.registerAction(id, new AnAction(label(lang, settings)) {
                @Override
                public void update(AnActionEvent e) {
                    e.getPresentation().setText(label(lang, settings));
                }

                @Override
                public void actionPerformed(AnActionEvent e) {
                    if (lang.equals(settings.language())) {
                        return;
                    }
                    settings.setLanguage(lang);
                    JOptionPane.showMessageDialog(frame, Messages.get("language.restart.message"),
                            Messages.get("language.restart.title"), JOptionPane.INFORMATION_MESSAGE);
                }
            });
            am.addToGroup("core.language", id, Constraints.LAST);
        }
        am.addToGroup(ActionIds.VIEW_MENU, "core.language", Constraints.LAST);
        ((DefaultActionGroup) am.getAction(ActionIds.VIEW_MENU)).addSeparator();
    }

    private static String label(String lang, AppSettings settings) {
        String name = Messages.get("language." + lang);
        return (lang.equals(settings.language()) ? "\u2022 " : "   ") + name;
    }
}
