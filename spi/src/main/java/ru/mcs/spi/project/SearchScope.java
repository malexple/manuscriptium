package ru.mcs.spi.project;

/** Поле универсального документа, по которому выполняется полнотекстовый запрос. */
public enum SearchScope {
    ALL,
    TITLE,
    CONTENT,
    METADATA,
    PATH,
    ATTRIBUTES
}
