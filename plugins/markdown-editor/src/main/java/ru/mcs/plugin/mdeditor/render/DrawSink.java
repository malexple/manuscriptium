package ru.mcs.plugin.mdeditor.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextLayout;

/** Тонкий рендер-контракт: экранная и recording-реализации получают одинаковые команды. */
public interface DrawSink {

    void fillRect(int x, int y, int width, int height, Color color);

    void fillRoundRect(int x, int y, int width, int height, int radius, Color color);

    void drawLine(int x1, int y1, int x2, int y2, int thickness, Color color);

    void drawText(TextLayout layout, String text, float x, float baseline, Font font, Color color);
}
