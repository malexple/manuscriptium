package ru.mcs.spi.service;

import java.util.Optional;

/** Реестр сервисов приложения. Ключ — класс/интерфейс сервиса; он должен быть виден потребителям. */
public interface ServiceRegistry {

    /** @throws IllegalArgumentException если сервис с таким типом уже зарегистрирован */
    <T> void register(Class<T> type, T instance);

    /** @throws IllegalStateException если сервис не найден */
    <T> T get(Class<T> type);

    <T> Optional<T> find(Class<T> type);
}
