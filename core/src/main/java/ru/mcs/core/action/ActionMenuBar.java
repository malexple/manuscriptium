package ru.mcs.core.action;

import java.util.function.Supplier;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import ru.mcs.spi.action.ActionGroup;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.DataContext;

/** Главное меню: верхний уровень — подгруппы группы MainMenu; пустые группы скрываются. */
public final class ActionMenuBar extends JMenuBar {

    private final ActionManagerImpl actions;
    private final Supplier<DataContext> ctx;

    public ActionMenuBar(ActionManagerImpl actions, Supplier<DataContext> ctx) {
        this.actions = actions;
        this.ctx = ctx;
        rebuild();
        actions.addChangeListener(() -> SwingUtilities.invokeLater(this::rebuild));
    }

    public void rebuild() {
        removeAll();
        if (actions.getAction(ActionIds.MAIN_MENU) instanceof ActionGroup root) {
            for (AnAction child : root.getChildren(null)) {
                if (child instanceof ActionGroup g && ActionMenus.hasContent(g)) {
                    add(ActionMenus.createMenu(g, g.getTemplatePresentation(), ActionPlaces.MAIN_MENU, ctx));
                }
            }
        }
        revalidate();
        repaint();
    }
}
