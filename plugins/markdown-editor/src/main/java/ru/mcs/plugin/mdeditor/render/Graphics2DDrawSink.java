package ru.mcs.plugin.mdeditor.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;

/** Экранный DrawSink поверх Graphics2D. */
public final class Graphics2DDrawSink implements DrawSink {

    private final Graphics2D graphics;

    public Graphics2DDrawSink(Graphics2D graphics) {
        this.graphics = graphics;
    }

    @Override
    public void fillRect(int x, int y, int width, int height, Color color) {
        graphics.setColor(color);
        graphics.fillRect(x, y, width, height);
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int radius, Color color) {
        graphics.setColor(color);
        graphics.fillRoundRect(x, y, width, height, radius * 2, radius * 2);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, int thickness, Color color) {
        graphics.setColor(color);
        if (x1 == x2) {
            graphics.fillRect(x1, Math.min(y1, y2), Math.max(1, thickness), Math.abs(y2 - y1) + thickness);
        } else if (y1 == y2) {
            graphics.fillRect(Math.min(x1, x2), y1, Math.abs(x2 - x1) + thickness, Math.max(1, thickness));
        } else {
            graphics.setStroke(new BasicStroke(Math.max(1, thickness), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawLine(x1, y1, x2, y2);
        }
    }

    @Override
    public void drawText(TextLayout layout, String text, float x, float baseline, Font font, Color color) {
        graphics.setFont(font);
        graphics.setColor(color);
        layout.draw(graphics, x, baseline);
    }
}
