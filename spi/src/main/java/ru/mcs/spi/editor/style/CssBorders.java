package ru.mcs.spi.editor.style;

import java.util.Objects;

/** Четыре границы блока после каскада. */
public record CssBorders(CssBorder top, CssBorder right, CssBorder bottom, CssBorder left) {

    public static final CssBorders NONE = new CssBorders(CssBorder.NONE, CssBorder.NONE,
            CssBorder.NONE, CssBorder.NONE);

    public CssBorders {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(left, "left");
    }
}
