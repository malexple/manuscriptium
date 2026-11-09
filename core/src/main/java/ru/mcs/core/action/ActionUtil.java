package ru.mcs.core.action;

import java.util.function.Supplier;
import ru.mcs.spi.action.ActionGroup;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;

/** Безопасный вызов update/actionPerformed: исключение плагина не должно ломать меню и тулбар. */
final class ActionUtil {

    private static final System.Logger LOG = System.getLogger(ActionUtil.class.getName());

    private ActionUtil() {
    }

    static AnActionEvent update(AnAction action, String place, DataContext ctx) {
        AnActionEvent e = new AnActionEvent(action.getTemplatePresentation().copy(), place, ctx);
        try {
            action.update(e);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.ERROR, "Action update failed: " + action.getClass().getName(), t);
            e.getPresentation().setEnabled(false);
        }
        return e;
    }

    static void perform(AnAction action, String place, DataContext ctx) {
        AnActionEvent e = update(action, place, ctx);
        if (!e.getPresentation().isEnabled() || !e.getPresentation().isVisible()) {
            return;
        }
        try {
            action.actionPerformed(e);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.ERROR, "Action failed: " + action.getClass().getName(), t);
        }
    }

    static AnAction[] children(ActionGroup group, AnActionEvent e) {
        try {
            AnAction[] result = group.getChildren(e);
            return result == null ? new AnAction[0] : result;
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.ERROR, "getChildren failed: " + group.getClass().getName(), t);
            return new AnAction[0];
        }
    }

    static DataContext context(Supplier<DataContext> supplier) {
        DataContext ctx = supplier.get();
        return ctx == null ? DataContext.EMPTY : ctx;
    }
}
