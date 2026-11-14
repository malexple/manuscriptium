package ru.mcs.spi.editor.style;

import java.util.Objects;

/** Локальный @font-face. source — URI из CSS, разрешается относительно каталога темы. */
public record FontFaceDefinition(String family, String source, int weight, boolean italic) {

    public FontFaceDefinition {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(source, "source");
        if (family.isBlank() || source.isBlank()) {
            throw new IllegalArgumentException("Font family and source must not be blank");
        }
        if (weight < 100 || weight > 900) {
            throw new IllegalArgumentException("Font weight must be in 100..900");
        }
    }
}
