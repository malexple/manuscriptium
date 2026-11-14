package ru.mcs.spi.editor.style;

import java.util.List;
import java.util.Objects;

/** Неизменяемое CSS-правило; selectors хранят исходные строки для независимости SPI от парсера. */
public record CssRule(List<String> selectors, List<CssDeclaration> declarations,
                      CssOrigin origin, int order) {

    public CssRule {
        selectors = List.copyOf(selectors);
        declarations = List.copyOf(declarations);
        Objects.requireNonNull(origin, "origin");
        if (selectors.isEmpty()) {
            throw new IllegalArgumentException("CSS rule must have at least one selector");
        }
        if (order < 0) {
            throw new IllegalArgumentException("order must be non-negative");
        }
    }
}
