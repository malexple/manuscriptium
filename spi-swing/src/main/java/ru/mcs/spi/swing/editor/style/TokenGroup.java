package ru.mcs.spi.swing.editor.style;

import java.util.List;
import java.util.Objects;

/**
 * Набор токенов одного языка или формата. {@code sample} — образец текста, {@code preview} разбирает его на токены,
 * чтобы страница настроек могла показать результат на светлом и тёмном фоне.
 */
public record TokenGroup(String id, String displayName, List<TokenDefinition> tokens, String sample,
                         TokenPreview preview) {

    public TokenGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(preview, "preview");
        tokens = List.copyOf(tokens);
    }
}
