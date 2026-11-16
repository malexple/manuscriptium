package ru.mcs.plugin.mdeditor.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import ru.mcs.plugin.mdeditor.draw.BlockWidget;
import ru.mcs.plugin.mdeditor.draw.DrawSink;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Read-only grid для неактивной таблицы; редактирование выполняется над исходным Markdown-span. */
public final class TableWidget extends AbstractBlockWidget {

    private static final int ROW_HEIGHT = 30;

    private final TableModel table;
    private final ComputedStyle style;
    private final Font font;
    private final String caption;

    TableWidget(int start, int end, String source, ComputedStyle style, Font font, String caption) {
        super(start, end);
        this.table = TableModel.parse(source);
        this.style = style;
        this.font = font.deriveFont(Math.max(13f, font.getSize2D()));
        this.caption = caption;
    }

    @Override
    public int height(int width) {
        int content = table == null ? WidgetText.lineHeight(font) + 16 : table.rowCount() * ROW_HEIGHT;
        return content + (caption == null ? 0 : WidgetText.lineHeight(font) + 4);
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        if (table == null) {
            WidgetText.draw(sink, "Invalid Markdown table", font, style.color(), bounds.x + 8,
                    bounds.y + 8 + WidgetText.baseline(font));
            return;
        }
        Color border = new Color(style.color().getRed(), style.color().getGreen(), style.color().getBlue(), 70);
        int columns = table.columnCount();
        int[] edges = new int[columns + 1];
        edges[0] = bounds.x;
        for (int column = 0; column < columns; column++) {
            edges[column + 1] = column == columns - 1 ? bounds.x + bounds.width
                    : bounds.x + Math.round((float) bounds.width * (column + 1) / columns);
        }
        for (int row = 0; row < table.rowCount(); row++) {
            int y = bounds.y + row * ROW_HEIGHT;
            if (row == 0) {
                sink.fillRect(bounds.x, y, bounds.width, ROW_HEIGHT,
                        style.backgroundColor() == null ? new Color(0xf6f8fa) : style.backgroundColor());
            }
            sink.drawLine(bounds.x, y, bounds.x + bounds.width, y, 1, border);
            for (int column = 0; column < columns; column++) {
                int left = edges[column];
                int right = edges[column + 1];
                sink.drawLine(left, y, left, y + ROW_HEIGHT, 1, border);
                String value = table.cell(row, column).replace("\\|", "|");
                int textWidth = WidgetText.width(value, font);
                int x = switch (table.alignment(column)) {
                    case LEFT -> left + 8;
                    case CENTER -> left + Math.max(8, (right - left - textWidth) / 2);
                    case RIGHT -> Math.max(left + 8, right - textWidth - 8);
                };
                WidgetText.draw(sink, value, row == 0 ? font.deriveFont(Font.BOLD) : font, style.color(),
                        x, y + (ROW_HEIGHT + WidgetText.baseline(font)) / 2 - 2);
            }
            sink.drawLine(bounds.x + bounds.width, y, bounds.x + bounds.width, y + ROW_HEIGHT, 1, border);
        }
        int tableHeight = table.rowCount() * ROW_HEIGHT;
        sink.drawLine(bounds.x, bounds.y + tableHeight, bounds.x + bounds.width,
                bounds.y + tableHeight, 1, border);
        if (caption != null) {
            WidgetText.draw(sink, caption, font, style.color(), bounds.x,
                    bounds.y + bounds.height - WidgetText.lineHeight(font) + WidgetText.baseline(font));
        }
    }

    public TableModel table() {
        return table;
    }
}
