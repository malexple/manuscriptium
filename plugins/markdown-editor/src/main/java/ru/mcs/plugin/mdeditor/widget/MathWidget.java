package ru.mcs.plugin.mdeditor.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import ru.mcs.plugin.mdeditor.render.DrawSink;

/** Асинхронный JLaTeXMath block; до готовности и при ошибке показывает исходную формулу. */
public final class MathWidget extends AbstractBlockWidget {

    private final String latex;
    private final WidgetRegistry.AsyncImage result;
    private final Font font;
    private final Color color;

    MathWidget(int start, int end, String latex, boolean display, float fontSize,
               Color color, WidgetRegistry registry) {
        super(start, end);
        this.latex = latex;
        this.font = new Font(Font.MONOSPACED, Font.PLAIN, 12).deriveFont(Math.max(12f, fontSize));
        this.color = color;
        result = registry.math(latex, display, Math.max(18f, fontSize * (display ? 1.25f : 1f)), color);
    }

    @Override
    public int height(int width) {
        return result.image != null ? result.image.getHeight() + 12 : WidgetText.lineHeight(font) + 20;
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        if (result.image != null) {
            int x = bounds.x + Math.max(0, (bounds.width - result.image.getWidth()) / 2);
            sink.drawImage(result.image, x, bounds.y + 6, result.image.getWidth(), result.image.getHeight());
            return;
        }
        String text = result.failed ? latex : "Rendering math…";
        WidgetText.draw(sink, text, font, color, bounds.x + 6,
                bounds.y + 10 + WidgetText.baseline(font));
    }

    static String blockLatex(String source) {
        String value = source.strip();
        if (value.startsWith("$$")) {
            value = value.substring(2);
        }
        if (value.endsWith("$$")) {
            value = value.substring(0, value.length() - 2);
        }
        return value.strip();
    }
}
