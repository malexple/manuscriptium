package ru.mcs.plugin.mdeditor.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import ru.mcs.editor.draw.BlockWidget;
import ru.mcs.editor.draw.DrawSink;
import ru.mcs.spi.swing.editor.style.ComputedStyle;

/** Свёрнутая YAML-карточка: клик переводит активный блок в source для редактирования. */
public final class FrontMatterWidget extends AbstractBlockWidget {

    private final String source;
    private final ComputedStyle style;
    private final Font font;

    FrontMatterWidget(int start, int end, String source, ComputedStyle style, Font font) {
        super(start, end);
        this.source = source;
        this.style = style;
        this.font = font.deriveFont(Font.BOLD, Math.max(13f, font.getSize2D()));
    }

    @Override
    public int height(int width) {
        return WidgetText.lineHeight(font) + 24;
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        Color background = style.backgroundColor() == null ? new Color(0xf6f8fa) : style.backgroundColor();
        sink.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, background);
        int fields = 0;
        for (String line : source.split("\\R")) {
            if (line.matches("[A-Za-z_][A-Za-z0-9_.-]*\\s*:.*")) {
                fields++;
            }
        }
        String title = source.lines().filter(line -> line.startsWith("title:")).findFirst()
                .map(line -> line.substring(6).strip()).filter(value -> !value.isEmpty())
                .orElse("Front matter");
        WidgetText.draw(sink, title + "  ·  " + fields + " fields", font, style.color(),
                bounds.x + 12, bounds.y + 12 + WidgetText.baseline(font));
    }
}
