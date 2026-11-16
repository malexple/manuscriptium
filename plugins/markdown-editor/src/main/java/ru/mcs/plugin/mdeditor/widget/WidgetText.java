package ru.mcs.plugin.mdeditor.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import ru.mcs.plugin.mdeditor.draw.DrawSink;

final class WidgetText {

    private static final FontRenderContext FRC = new FontRenderContext(AffineTransform.getScaleInstance(2, 2),
            true, true);

    private WidgetText() {
    }

    static void draw(DrawSink sink, String text, Font font, Color color, int x, int baseline) {
        if (text != null && !text.isEmpty()) {
            TextLayout layout = new TextLayout(text, font, FRC);
            sink.drawText(layout, text, x, baseline, font, color);
        }
    }

    static int lineHeight(Font font) {
        TextLayout layout = new TextLayout("Ag", font, FRC);
        return Math.max(1, Math.round(layout.getAscent() + layout.getDescent() + layout.getLeading()));
    }

    static int baseline(Font font) {
        return Math.round(new TextLayout("Ag", font, FRC).getAscent());
    }

    static int width(String text, Font font) {
        return text.isEmpty() ? 0 : Math.round(new TextLayout(text, font, FRC).getAdvance());
    }
}
