package ru.mcs.spi.editor.style;

import java.util.List;
import java.util.Objects;

/** Динамическое состояние узла. ancestors упорядочены от корня к непосредственному родителю. */
public record ElementState(String tag, String id, boolean hover, int childIndex,
                           List<StyleElement> ancestors) {

    public static final ElementState DEFAULT = new ElementState(null, "", false, 0, List.of());

    public ElementState {
        id = Objects.requireNonNullElse(id, "");
        ancestors = List.copyOf(ancestors);
    }

    public static ElementState tag(String tag) {
        return new ElementState(Objects.requireNonNull(tag, "tag"), "", false, 0, List.of());
    }

    public ElementState withHover(boolean value) {
        return new ElementState(tag, id, value, childIndex, ancestors);
    }
}
