package ru.mcs.plugin.mdeditor.action;

import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;

/** Выполняет именно зарегистрированное действие для keymap и popup, включая его update-контракт. */
public final class EditorActionDispatcher {

    private static final System.Logger LOG = System.getLogger(EditorActionDispatcher.class.getName());

    private EditorActionDispatcher() {
    }

    public static AnActionEvent update(ActionManager actions, String id, String place) {
        AnAction action = actions == null ? null : actions.getAction(id);
        if (action == null) {
            return null;
        }
        AnActionEvent event = new AnActionEvent(action.getTemplatePresentation().copy(), place, DataContext.EMPTY);
        try {
            action.update(event);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Editor action update failed: " + id, e);
            event.getPresentation().setEnabled(false);
        }
        return event;
    }

    public static boolean perform(ActionManager actions, String id, String place) {
        AnAction action = actions == null ? null : actions.getAction(id);
        AnActionEvent event = update(actions, id, place);
        if (action == null || event == null || !event.getPresentation().isEnabled()
                || !event.getPresentation().isVisible()) {
            return false;
        }
        try {
            action.actionPerformed(event);
            return true;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Editor action failed: " + id, e);
            return false;
        }
    }
}
