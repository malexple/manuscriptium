package ru.mcs.spi.action;

import java.util.ArrayList;
import java.util.List;

/** Группа с изменяемым списком дочерних действий (потокобезопасна). */
public class DefaultActionGroup extends ActionGroup {

    private static final System.Logger LOG = System.getLogger(DefaultActionGroup.class.getName());

    private final List<AnAction> children = new ArrayList<>();

    public DefaultActionGroup() {
        super();
    }

    public DefaultActionGroup(String text, boolean popup) {
        super(text, popup);
    }

    public synchronized void add(AnAction action) {
        addInternal(action, children.size());
    }

    /** Вставка с учётом {@link Constraints}; {@code manager} нужен для разбора BEFORE/AFTER по ID. */
    public synchronized void add(AnAction action, Constraints constraints, ActionManager manager) {
        int index = switch (constraints.anchor()) {
            case FIRST -> 0;
            case LAST -> children.size();
            case BEFORE, AFTER -> {
                int i = indexOfId(constraints.relativeTo(), manager);
                if (i < 0) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Unknown relative action id '" + constraints.relativeTo() + "', appending to the end");
                    yield children.size();
                }
                yield constraints.anchor() == Constraints.Anchor.BEFORE ? i : i + 1;
            }
        };
        addInternal(action, index);
    }

    public synchronized void addSeparator() {
        children.add(Separator.INSTANCE);
    }

    public synchronized void remove(AnAction action) {
        children.remove(action);
    }

    public synchronized void removeAll() {
        children.clear();
    }

    @Override
    public synchronized AnAction[] getChildren(AnActionEvent e) {
        return children.toArray(AnAction[]::new);
    }

    private void addInternal(AnAction action, int index) {
        if (action != Separator.INSTANCE && children.contains(action)) {
            return;
        }
        children.add(index, action);
    }

    private int indexOfId(String id, ActionManager manager) {
        if (id == null || manager == null) {
            return -1;
        }
        for (int i = 0; i < children.size(); i++) {
            if (id.equals(manager.getId(children.get(i)))) {
                return i;
            }
        }
        return -1;
    }
}
