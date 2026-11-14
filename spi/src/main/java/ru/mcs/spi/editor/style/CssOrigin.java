package ru.mcs.spi.editor.style;

/** Слои каскада в порядке возрастания приоритета при равной специфичности. */
public enum CssOrigin {
    DEFAULT, THEME, BASE_USER, THEME_USER
}
