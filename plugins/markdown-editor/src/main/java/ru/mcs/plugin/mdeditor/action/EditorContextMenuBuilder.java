package ru.mcs.plugin.mdeditor.action;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import ru.mcs.spi.action.ActionGroup;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DefaultActionGroup;
import ru.mcs.spi.action.Separator;

/** Собирает ПКМ из тех же ActionGroup, что и главное меню, добавляя блок-специфичную команду. */
public final class EditorContextMenuBuilder {

    private final ActionManager actions;

    public EditorContextMenuBuilder(ActionManager actions) {
        this.actions = actions;
    }

    public ActionGroup group(EditorBlockContext context) {
        DefaultActionGroup root = new DefaultActionGroup("", false);
        add(root, EditorActionIds.UNDO);
        add(root, EditorActionIds.REDO);
        add(root, EditorActionIds.CUT);
        add(root, EditorActionIds.COPY);
        add(root, EditorActionIds.PASTE);
        root.addSeparator();
        if (context.code()) {
            add(root, EditorActionIds.CODE_BLOCK);
        } else if (context.table()) {
            add(root, EditorActionIds.TABLE);
        } else if (context.math()) {
            add(root, EditorActionIds.MATH_BLOCK);
        } else if (context.image()) {
            add(root, EditorActionIds.IMAGE);
        } else if (context.link()) {
            add(root, EditorActionIds.LINK);
        }
        add(root, EditorActionIds.PARAGRAPH_MENU);
        add(root, EditorActionIds.FORMAT_MENU);
        add(root, EditorActionIds.INSERT_MENU);
        root.addSeparator();
        add(root, EditorActionIds.SELECT_ALL);
        add(root, EditorActionIds.DELETE_BLOCK);
        add(root, EditorActionIds.WORD_COUNT);
        return root;
    }

    public List<String> actionIds(EditorBlockContext context) {
        List<String> ids = new ArrayList<>();
        for (AnAction child : group(context).getChildren(null)) {
            if (!(child instanceof Separator)) {
                ids.add(actions.getId(child));
            }
        }
        return ids;
    }

    public JPopupMenu build(EditorBlockContext context) {
        JPopupMenu popup = new JPopupMenu();
        fill(popup, group(context));
        return popup;
    }

    private void fill(JPopupMenu popup, ActionGroup group) {
        for (AnAction child : group.getChildren(null)) {
            if (child instanceof Separator) {
                if (popup.getComponentCount() > 0
                        && !(popup.getComponent(popup.getComponentCount() - 1) instanceof JPopupMenu.Separator)) {
                    popup.addSeparator();
                }
            } else if (child instanceof ActionGroup subgroup) {
                AnActionEvent event = EditorActionDispatcher.update(actions, actions.getId(subgroup), ActionPlaces.POPUP);
                if (event != null && event.getPresentation().isVisible()) {
                    JMenu menu = new JMenu(event.getPresentation().getText());
                    fill(menu.getPopupMenu(), subgroup);
                    if (menu.getMenuComponentCount() > 0) {
                        popup.add(menu);
                    }
                }
            } else {
                String id = actions.getId(child);
                AnActionEvent event = EditorActionDispatcher.update(actions, id, ActionPlaces.POPUP);
                if (event == null || !event.getPresentation().isVisible()) {
                    continue;
                }
                JMenuItem item = new JMenuItem(event.getPresentation().getText(), event.getPresentation().getIcon());
                item.setEnabled(event.getPresentation().isEnabled());
                item.setToolTipText(event.getPresentation().getDescription());
                item.addActionListener(e -> EditorActionDispatcher.perform(actions, id, ActionPlaces.POPUP));
                popup.add(item);
            }
        }
    }

    private void add(DefaultActionGroup group, String id) {
        AnAction action = actions.getAction(id);
        if (action != null) {
            group.add(action);
        }
    }
}
