package ru.mcs.spi.editor.style;

import java.util.Locale;
import java.util.Objects;

/** Одно CSS-объявление без раскрытия var() и shorthand-свойств. */
public record CssDeclaration(String name, String value, boolean important, int line) {

    public CssDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        name = name.trim().toLowerCase(Locale.ROOT);
        value = value.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("CSS property name must not be empty");
        }
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
    }
}
