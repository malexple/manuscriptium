package ru.mcs.spi.service;

import java.util.Map;

/**
 * Компонент с сохраняемым состоянием. Состояние — дерево из Map (ключи — строки), List, String, Number, Boolean и null:
 * никакой рефлексии и сериализации классов, поэтому оно безопасно для плагинов в изолированных модулях.
 * Числа после загрузки могут иметь другой тип (Integer/Long/Double) — читайте их как {@link Number}.
 * {@code getState()} вызывается из любого потока и должен быть потокобезопасным и быстрым.
 */
public interface PersistentStateComponent {

    Map<String, Object> getState();

    /** Вызывается один раз при регистрации, если сохранённое состояние существует. */
    void loadState(Map<String, Object> state);
}
