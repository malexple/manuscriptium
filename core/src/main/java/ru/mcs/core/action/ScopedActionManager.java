package ru.mcs.core.action;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.Constraints;

/** Представление ActionManager для одного плагина: запоминает регистрации, чтобы снять их при выгрузке. */
final class ScopedActionManager implements ActionManager {

    private record GroupLink(String groupId, String actionId) {
    }

    private final ActionManagerImpl delegate;
    private final Set<String> ownedIds = ConcurrentHashMap.newKeySet();
    private final Set<GroupLink> links = ConcurrentHashMap.newKeySet();

    ScopedActionManager(ActionManagerImpl delegate) {
        this.delegate = delegate;
    }

    @Override
    public void registerAction(String id, AnAction action) {
        delegate.registerAction(id, action);
        ownedIds.add(id);
    }

    @Override
    public void unregisterAction(String id) {
        delegate.unregisterAction(id);
        ownedIds.remove(id);
    }

    @Override
    public AnAction getAction(String id) {
        return delegate.getAction(id);
    }

    @Override
    public String getId(AnAction action) {
        return delegate.getId(action);
    }

    @Override
    public void addToGroup(String groupId, String actionId, Constraints constraints) {
        delegate.addToGroup(groupId, actionId, constraints);
        links.add(new GroupLink(groupId, actionId));
    }

    @Override
    public void removeFromGroup(String groupId, String actionId) {
        delegate.removeFromGroup(groupId, actionId);
        links.remove(new GroupLink(groupId, actionId));
    }

    Set<String> ownedIds() {
        return Set.copyOf(ownedIds);
    }

    void cleanup() {
        for (GroupLink l : Set.copyOf(links)) {
            delegate.removeFromGroup(l.groupId(), l.actionId());
        }
        links.clear();
        for (String id : Set.copyOf(ownedIds)) {
            delegate.unregisterAction(id);
        }
        ownedIds.clear();
    }
}
