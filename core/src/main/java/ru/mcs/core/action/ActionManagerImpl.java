package ru.mcs.core.action;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DefaultActionGroup;

public final class ActionManagerImpl implements ActionManager {

    private final Map<String, AnAction> actions = new ConcurrentHashMap<>();
    private final Map<AnAction, String> ids = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<String, ScopedActionManager> scopes = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void registerAction(String id, AnAction action) {
        if (id == null || id.isBlank() || action == null) {
            throw new IllegalArgumentException("id and action are required");
        }
        synchronized (this) {
            if (actions.containsKey(id)) {
                throw new IllegalArgumentException("Action already registered: " + id);
            }
            String existing = ids.get(action);
            if (existing != null) {
                throw new IllegalArgumentException("Action instance already registered as: " + existing);
            }
            actions.put(id, action);
            ids.put(action, id);
        }
        fireChanged();
    }

    @Override
    public void unregisterAction(String id) {
        AnAction removed;
        synchronized (this) {
            removed = actions.remove(id);
            if (removed == null) {
                return;
            }
            ids.remove(removed);
            for (AnAction a : actions.values()) {
                if (a instanceof DefaultActionGroup g) {
                    g.remove(removed);
                }
            }
        }
        fireChanged();
    }

    @Override
    public AnAction getAction(String id) {
        return id == null ? null : actions.get(id);
    }

    @Override
    public String getId(AnAction action) {
        return ids.get(action);
    }

    @Override
    public void addToGroup(String groupId, String actionId, Constraints constraints) {
        DefaultActionGroup group = requireGroup(groupId);
        AnAction action = getAction(actionId);
        if (action == null) {
            throw new IllegalArgumentException("Unknown action: " + actionId);
        }
        group.add(action, constraints, this);
        fireChanged();
    }

    @Override
    public void removeFromGroup(String groupId, String actionId) {
        AnAction group = getAction(groupId);
        AnAction action = getAction(actionId);
        if (group instanceof DefaultActionGroup g && action != null) {
            g.remove(action);
            fireChanged();
        }
    }

    private DefaultActionGroup requireGroup(String groupId) {
        if (getAction(groupId) instanceof DefaultActionGroup g) {
            return g;
        }
        throw new IllegalArgumentException("Unknown or non-editable group: " + groupId);
    }

    /** Слушатель вызывается в потоке, изменившем реестр; UI должен сам перейти на EDT. */
    public Runnable addChangeListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Обновляет меню и toolbar после изменения контекста, когда сам реестр действий не менялся. */
    public void updateActions() {
        fireChanged();
    }

    private void fireChanged() {
        for (Runnable l : listeners) {
            l.run();
        }
    }

    public ActionManager scopedFor(String pluginId) {
        return scopes.computeIfAbsent(pluginId, id -> new ScopedActionManager(this));
    }

    /** Снять всё, что зарегистрировал плагин (вызывается после остановки плагина). */
    public void unregisterAll(String pluginId) {
        ScopedActionManager scope = scopes.remove(pluginId);
        if (scope != null) {
            scope.cleanup();
        }
    }

    public Set<String> getActionIds() {
        return new TreeSet<>(actions.keySet());
    }

    public Set<String> pluginActionIds() {
        Set<String> result = new TreeSet<>();
        for (ScopedActionManager s : scopes.values()) {
            result.addAll(s.ownedIds());
        }
        return result;
    }

    /** ID дочерних элементов группы (для диагностики и тестов). */
    public List<String> childIds(String groupId) {
        AnAction a = getAction(groupId);
        if (!(a instanceof DefaultActionGroup g)) {
            return List.of();
        }
        return java.util.Arrays.stream(g.getChildren(null)).map(this::getId).toList();
    }
}
