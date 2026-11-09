package ru.mcs.spi.service;

/**
 * Менеджер сохранения состояния. Состояние записывается автоматически: периодически (если изменилось),
 * при выгрузке плагина и при выходе из приложения.
 */
public interface StateManager {

    /**
     * Регистрирует компонент и сразу загружает в него сохранённое состояние.
     *
     * @param id уникальный в рамках плагина, символы [A-Za-z0-9._-]; для плагина автоматически получает префикс его ID
     * @throws IllegalArgumentException если ID некорректен или занят
     */
    void register(String id, Scope scope, PersistentStateComponent component);

    /** Сохраняет текущее состояние и снимает компонент. */
    void unregister(String id);
}
