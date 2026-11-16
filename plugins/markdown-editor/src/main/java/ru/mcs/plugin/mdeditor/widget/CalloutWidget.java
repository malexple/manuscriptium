package ru.mcs.plugin.mdeditor.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.mcs.plugin.mdeditor.draw.BlockWidget;
import ru.mcs.plugin.mdeditor.draw.DrawSink;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Obsidian-callout > [!type] с цветной плашкой и векторным маркером. */
public final class CalloutWidget extends AbstractBlockWidget {

    private final String type;
    private final String title;
    private final List<String> body;
    private final boolean collapsed;
    private final ComputedStyle style;
    private final Font font;
    private final Font titleFont;

    CalloutWidget(int start, int end, String source, ComputedStyle style, Font font) {
        super(start, end);
        this.style = style;
        this.font = font;
        this.titleFont = font.deriveFont(Font.BOLD);
        List<String> lines = source.lines().toList();
        String marker = lines.isEmpty() ? "> [!note]" : lines.getFirst().replaceFirst("^\\s*>\\s?", "");
        if (!marker.matches("\\[![^]]+].*")) {
            marker = "[!note]";
        }
        int close = marker.indexOf(']');
        type = marker.substring(2, close).toLowerCase(Locale.ROOT);
        collapsed = close + 1 < marker.length() && marker.charAt(close + 1) == '-';
        String explicitTitle = marker.substring(close + 1 + (collapsed ? 1 : 0)).strip();
        title = explicitTitle.isEmpty() ? type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1) : explicitTitle;
        body = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            body.add(lines.get(index).replaceFirst("^\\s*>\\s?", ""));
        }
    }

    @Override
    public int height(int width) {
        return 20 + WidgetText.lineHeight(titleFont)
                + (collapsed ? 0 : Math.max(1, body.size()) * (WidgetText.lineHeight(font) + 3));
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        Color accent = switch (type) {
            case "warning", "caution", "attention" -> new Color(0xd4a72c);
            case "danger", "error", "bug", "failure" -> new Color(0xcf222e);
            case "success", "check", "done" -> new Color(0x1a7f37);
            case "question", "help", "faq" -> new Color(0x8250df);
            default -> new Color(0x0969da);
        };
        Color background = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 20);
        sink.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, background);
        sink.fillRoundRect(bounds.x, bounds.y, 5, bounds.height, 3, accent);
        int y = bounds.y + 10 + WidgetText.baseline(titleFont);
        sink.fillRoundRect(bounds.x + 14, y - WidgetText.baseline(titleFont), 10, 10, 3, accent);
        WidgetText.draw(sink, title, titleFont, accent, bounds.x + 32, y);
        y += 7;
        if (collapsed) {
            return;
        }
        for (String line : body) {
            y += WidgetText.lineHeight(font);
            WidgetText.draw(sink, line, font, style.color(), bounds.x + 16, y);
        }
    }
}
