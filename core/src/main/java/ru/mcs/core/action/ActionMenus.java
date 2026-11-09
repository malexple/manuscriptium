package ru.mcs.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import ru.mcs.spi.action.ActionGroup;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.action.Presentation;
import ru.mcs.spi.action.Separator;

/** Построение Swing-меню из групп действий. Элементы вычисляются в момент показа (update). */
public final class ActionMenus {

    private ActionMenus() {
    }

    public static JPopupMenu createPopup(ActionGroup group, String place, Supplier<DataContext> ctx) {
        JPopupMenu popup = new JPopupMenu();
        buildItems(group, place, ctx).forEach(popup::add);
        return popup;
    }

    static JMenu createMenu(ActionGroup group, Presentation presentation, String place, Supplier<DataContext> ctx) {
        JMenu menu = new JMenu(presentation.getText());
        menu.setIcon(presentation.getIcon());
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                buildItems(group, place, ctx).forEach(menu::add);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        return menu;
    }

    static List<JComponent> buildItems(ActionGroup group, String place, Supplier<DataContext> ctx) {
        List<JComponent> out = new ArrayList<>();
        fill(out, group, place, ctx);
        while (!out.isEmpty() && out.get(out.size() - 1) instanceof JPopupMenu.Separator) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static void fill(List<JComponent> out, ActionGroup group, String place, Supplier<DataContext> ctx) {
        AnActionEvent ge = ActionUtil.update(group, place, ActionUtil.context(ctx));
        if (!ge.getPresentation().isVisible()) {
            return;
        }
        for (AnAction child : ActionUtil.children(group, ge)) {
            if (child instanceof Separator) {
                addSeparator(out);
            } else if (child instanceof ActionGroup sub) {
                AnActionEvent se = ActionUtil.update(sub, place, ActionUtil.context(ctx));
                if (!se.getPresentation().isVisible() || !hasContent(sub)) {
                    continue;
                }
                if (sub.isPopup()) {
                    out.add(createMenu(sub, se.getPresentation(), place, ctx));
                } else {
                    fill(out, sub, place, ctx);
                }
            } else {
                JMenuItem item = createItem(child, place, ctx);
                if (item != null) {
                    out.add(item);
                }
            }
        }
    }

    private static JMenuItem createItem(AnAction action, String place, Supplier<DataContext> ctx) {
        Presentation p = ActionUtil.update(action, place, ActionUtil.context(ctx)).getPresentation();
        if (!p.isVisible()) {
            return null;
        }
        JMenuItem item = new JMenuItem(p.getText(), p.getIcon());
        item.setEnabled(p.isEnabled());
        item.setToolTipText(p.getDescription());
        item.addActionListener(ev -> ActionUtil.perform(action, place, ActionUtil.context(ctx)));
        return item;
    }

    private static void addSeparator(List<JComponent> out) {
        if (!out.isEmpty() && !(out.get(out.size() - 1) instanceof JPopupMenu.Separator)) {
            out.add(new JPopupMenu.Separator());
        }
    }

    /** Структурная проверка: есть ли в группе хоть один не-разделитель (рекурсивно). */
    static boolean hasContent(ActionGroup group) {
        try {
            for (AnAction child : group.getChildren(null)) {
                if (child instanceof Separator) {
                    continue;
                }
                if (child instanceof ActionGroup sub) {
                    if (hasContent(sub)) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }
}
