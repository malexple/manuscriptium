package ru.mcs.spi.service;

/**
 * Где хранится состояние. APPLICATION — каталог данных приложения на этой машине.
 * PROJECT — внутри project (в Фазе 4 это {@code <project>/.manuscriptium/state}; сейчас временный рабочий каталог).
 */
public enum Scope { APPLICATION, PROJECT }
