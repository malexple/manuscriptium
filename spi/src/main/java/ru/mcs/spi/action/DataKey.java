package ru.mcs.spi.action;

/** Типизированный ключ контекста данных (например, «активная заметка»). */
public record DataKey<T>(String name, Class<T> type) {

    public static <T> DataKey<T> create(String name, Class<T> type) {
        return new DataKey<>(name, type);
    }
}
