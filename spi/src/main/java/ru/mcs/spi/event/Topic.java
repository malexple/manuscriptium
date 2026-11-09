package ru.mcs.spi.event;

import java.util.Objects;

/** Тема шины сообщений. Тип слушателя — публичный интерфейс из экспортируемого пакета, все методы void. */
public record Topic<L>(String name, Class<L> listenerClass) {

    public Topic {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(listenerClass, "listenerClass");
        if (!listenerClass.isInterface()) {
            throw new IllegalArgumentException("Listener type must be an interface: " + listenerClass.getName());
        }
    }

    public static <L> Topic<L> create(String name, Class<L> listenerClass) {
        return new Topic<>(name, listenerClass);
    }
}
