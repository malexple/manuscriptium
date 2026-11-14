package ru.mcs.spi.editor.style;

import java.util.Objects;

/** Верхняя, правая, нижняя и левая величины margin/padding. */
public record CssEdges(CssLength top, CssLength right, CssLength bottom, CssLength left) {

    public static final CssEdges ZERO = new CssEdges(CssLength.ZERO, CssLength.ZERO,
            CssLength.ZERO, CssLength.ZERO);

    public CssEdges {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(left, "left");
    }

    public static CssEdges all(CssLength value) {
        return new CssEdges(value, value, value, value);
    }
}
