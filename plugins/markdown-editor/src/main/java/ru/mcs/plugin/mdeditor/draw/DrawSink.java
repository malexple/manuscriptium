package ru.mcs.plugin.mdeditor.draw;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;

/** Тонкий рендер-контракт: экранная и recording-реализации получают одинаковые команды. */
public interface DrawSink {

    void fillRect(int x, int y, int width, int height, Color color);

    void fillRoundRect(int x, int y, int width, int height, int radius, Color color);

    void drawLine(int x1, int y1, int x2, int y2, int thickness, Color color);

    void drawText(TextLayout layout, String text, float x, float baseline, Font font, Color color);

    default void drawImage(BufferedImage image, int x, int y, int width, int height) {
        // Recording-sinks старых текстовых тестов могут не учитывать растровые команды.
    }
}
