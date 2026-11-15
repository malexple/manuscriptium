package ru.mcs.spi.editor;

/** Неизменяемый запрос стороннему renderer; содержит только JDK-типы. */
public record BlockRenderRequest(
        String infoString, String source, int widthPx, boolean dark, double scale) {

    public BlockRenderRequest {
        infoString = infoString == null ? "" : infoString.strip();
        source = source == null ? "" : source;
        if (widthPx <= 0) {
            throw new IllegalArgumentException("widthPx must be positive");
        }
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("scale must be positive");
        }
    }
}
