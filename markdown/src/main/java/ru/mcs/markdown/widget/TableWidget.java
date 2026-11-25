package ru.mcs.markdown.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import ru.mcs.editor.draw.DrawSink;
import ru.mcs.spi.swing.editor.style.ComputedStyle;

/** Сетка таблицы: ширина колонок по тексту, перенос длинных ячеек; редактирование идёт в исходном Markdown. */
public final class TableWidget extends AbstractBlockWidget {

    private static final int MIN_LINE_HEIGHT = 18;

    private final TableModel table;
    private final TableGrid grid;
    private final List<TableModel.Alignment> alignments;
    private final ComputedStyle style;
    private final Font font;
    private final Font boldFont;
    private final TableGrid.Metrics metrics;
    private final int lineHeight;
    private final String caption;
    private Styles styles;

    TableWidget(int start, int end, String source, ComputedStyle style, Font font, String caption) {
        super(start, end);
        this.table = TableModel.parse(source);
        this.grid = TableGrid.parse(source, start);
        List<TableModel.Alignment> columns = new ArrayList<>();
        if (table != null) {
            for (int column = 0; column < table.columnCount(); column++) {
                columns.add(table.alignment(column));
            }
        }
        this.alignments = columns;
        this.style = style;
        Font regular = font.deriveFont(Math.max(13f, font.getSize2D()));
        Font bold = regular.deriveFont(Font.BOLD);
        this.font = regular;
        this.boldFont = bold;
        this.metrics = (text, header) -> WidgetText.width(text, header ? bold : regular);
        this.lineHeight = Math.max(MIN_LINE_HEIGHT, WidgetText.lineHeight(regular));
        this.caption = caption;
    }

    /** Цвета из CSS темы: заголовок (th), нечётные/чётные строки тела (tr:nth-child), граница (td); null — по умолчанию. */
    public record Styles(Color header, Color odd, Color even, Color border) {
    }

    public void setStyles(Styles styles) {
        this.styles = styles;
    }

    @Override
    public int height(int width) {
        int content = grid == null || table == null ? WidgetText.lineHeight(font) + 16
                : grid.height(layout(0, 0, width));
        return content + (caption == null ? 0 : WidgetText.lineHeight(font) + 4);
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        if (table == null || grid == null) {
            WidgetText.draw(sink, "Invalid Markdown table", font, style.color(), bounds.x + 8,
                    bounds.y + 8 + WidgetText.baseline(font));
            return;
        }
        Color border = styles != null && styles.border() != null ? styles.border()
                : new Color(style.color().getRed(), style.color().getGreen(), style.color().getBlue(), 70);
        TableGrid.Geometry geometry = grid.geometry(layout(bounds.x, bounds.y, bounds.width));
        int right = bounds.x + bounds.width;
        for (int row = 0; row < grid.rowCount(); row++) {
            int top = bounds.y + geometry.top(row);
            int height = geometry.top(row + 1) - geometry.top(row);
            Color background = rowBackground(row);
            if (background != null) {
                sink.fillRect(bounds.x, top, bounds.width, height, background);
            }
            sink.drawLine(bounds.x, top, right, top, 1, border);
            Font cellFont = row == 0 ? boldFont : font;
            for (int column = 0; column < grid.columnCount(); column++) {
                int left = bounds.x + geometry.edge(column);
                sink.drawLine(left, top, left, top + height, 1, border);
                List<TableGrid.Line> lines = geometry.lines(row, column);
                for (int index = 0; index < lines.size(); index++) {
                    TableGrid.Line line = lines.get(index);
                    int lineTop = top + TableGrid.VERTICAL_PADDING + index * lineHeight;
                    int baseline = lineTop + (lineHeight + WidgetText.baseline(cellFont)) / 2 - 2;
                    WidgetText.draw(sink, line.text(), cellFont, style.color(), bounds.x + line.x(), baseline);
                }
            }
            sink.drawLine(right, top, right, top + height, 1, border);
        }
        int tableHeight = geometry.height();
        sink.drawLine(bounds.x, bounds.y + tableHeight, right, bounds.y + tableHeight, 1, border);
        if (caption != null) {
            WidgetText.draw(sink, caption, font, style.color(), bounds.x,
                    bounds.y + bounds.height - WidgetText.lineHeight(font) + WidgetText.baseline(font));
        }
    }

    private Color rowBackground(int row) {
        if (styles == null) {
            return row == 0 ? (style.backgroundColor() == null ? new Color(0xf6f8fa) : style.backgroundColor()) : null;
        }
        if (row == 0) {
            return styles.header();
        }
        return row % 2 == 0 ? styles.even() : styles.odd();
    }

    public TableModel table() {
        return table;
    }

    @Override
    public Rectangle caretRect(int offset, int x, int y, int width) {
        if (grid == null || offset < sourceStart() || offset > sourceEnd()) {
            return null;
        }
        return grid.caretRect(offset, layout(x, y, width));
    }

    @Override
    public int hitOffset(Point point, int x, int y, int width) {
        return grid == null ? -1 : grid.hitOffset(point, layout(x, y, width));
    }

    @Override
    public List<Rectangle> selectionRects(int start, int end, int x, int y, int width) {
        return grid == null ? List.of() : grid.selectionRects(start, end, layout(x, y, width));
    }

    private TableGrid.Layout layout(int x, int y, int width) {
        return new TableGrid.Layout(x, y, width, lineHeight, metrics, alignments);
    }
}
