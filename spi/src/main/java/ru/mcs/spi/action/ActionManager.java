package ru.mcs.spi.action;

/** Реестр действий и групп. Потокобезопасен; изменения UI применяются на EDT самим приложением. */
public interface ActionManager {

    /** @throws IllegalArgumentException если ID занят или действие уже зарегистрировано под другим ID */
    void registerAction(String id, AnAction action);

    void unregisterAction(String id);

    AnAction getAction(String id);

    /** @return ID зарегистрированного действия или null */
    String getId(AnAction action);

    /**
     * Добавить зарегистрированное действие в зарегистрированную {@link DefaultActionGroup}.
     * Если BEFORE/AFTER ссылается на неизвестный ID, действие добавляется в конец.
     *
     * @throws IllegalArgumentException если группа или действие не найдены
     */
    void addToGroup(String groupId, String actionId, Constraints constraints);

    void removeFromGroup(String groupId, String actionId);
}
