package ru.mcs.spi.swing.editor.style;

import java.util.List;

/** Разбор образца текста на токены для предпросмотра на странице цветовой схемы. */
@FunctionalInterface
public interface TokenPreview {

    List<TokenSpan> highlight(String text);
}
