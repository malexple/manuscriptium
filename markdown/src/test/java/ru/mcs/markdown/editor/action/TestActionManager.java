package ru.mcs.markdown.editor.action;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.swing.action.DefaultActionGroup;

/** Минимальный SPI-only ActionManager для тестов меню и keymap без зависимости от core. */
final class TestActionManager implements ActionManager {

    private final Map<String, AnAction> actions = new LinkedHashMap<>();
    private final Map<AnAction, String> ids = new IdentityHashMap<>();

    @Override
    public void registerAction(String id, AnAction action) {
        actions.put(id, action);
        ids.put(action, id);
    }

    @Override
    public void unregisterAction(String id) {
        ids.remove(actions.remove(id));
    }

    @Override
    public AnAction getAction(String id) {
        return actions.get(id);
    }

    @Override
    public String getId(AnAction action) {
        return ids.get(action);
    }

    @Override
    public void addToGroup(String groupId, String actionId, Constraints constraints) {
        ((DefaultActionGroup) getAction(groupId)).add(getAction(actionId), constraints, this);
    }

    @Override
    public void removeFromGroup(String groupId, String actionId) {
        ((DefaultActionGroup) getAction(groupId)).remove(getAction(actionId));
    }

    static TestActionManager editorActions() {
        TestActionManager manager = new TestActionManager();
        for (String id : EditorActionIds.ALL) {
            manager.registerAction(id, new AnAction(id) {
                @Override
                public void actionPerformed(ru.mcs.spi.swing.action.AnActionEvent event) {
                }
            });
        }
        for (String id : new String[]{EditorActionIds.PARAGRAPH_MENU, EditorActionIds.FORMAT_MENU,
                EditorActionIds.INSERT_MENU}) {
            manager.registerAction(id, new DefaultActionGroup(id, true));
        }
        return manager;
    }
}
