package ru.mcs.spi.editor.style;

import java.util.List;
import java.util.Objects;

/** Минимальное описание DOM-подобного узла для селекторов и ancestor-цепочек. */
public record StyleElement(String tag, String id, List<String> classes, int childIndex) {

    public StyleElement {
        Objects.requireNonNull(tag, "tag");
        tag = tag.trim();
        id = Objects.requireNonNullElse(id, "");
        classes = List.copyOf(classes);
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("tag must not be empty");
        }
    }

    public static StyleElement of(String tag) {
        return new StyleElement(tag, "", List.of(), 0);
    }
}
