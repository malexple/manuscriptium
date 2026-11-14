package ru.mcs.spi.editor.style;

import java.awt.Color;
import java.util.Objects;

/** Разрешённая граница одной стороны блока. */
public record CssBorder(CssLength width, String style, Color color) {

    public static final CssBorder NONE = new CssBorder(CssLength.ZERO, "none", new Color(0, true));

    public CssBorder {
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(color, "color");
    }

    public boolean visible() {
        return width.value() > 0 && !"none".equals(style) && color.getAlpha() > 0;
    }
}
