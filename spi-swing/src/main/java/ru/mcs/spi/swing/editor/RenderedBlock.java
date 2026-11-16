package ru.mcs.spi.swing.editor;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Готовое растровое представление блока и доступная текстовая альтернатива. */
public record RenderedBlock(BufferedImage image, String altText) {

    public RenderedBlock {
        Objects.requireNonNull(image, "image");
        altText = altText == null ? "" : altText;
    }
}
