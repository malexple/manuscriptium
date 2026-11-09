package ru.mcs.spi.service;

/**
 * Где хранится состояние. APPLICATION — каталог данных приложения на этой машине.
 * VAULT — внутри vault (в Фазе 4 это {@code <vault>/.manuscriptium/state}; сейчас временный рабочий каталог).
 */
public enum Scope { APPLICATION, VAULT }
