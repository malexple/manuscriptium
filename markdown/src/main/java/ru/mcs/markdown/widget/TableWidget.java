package ru.mcs.markdown.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import ru.mcs.editor.draw.DrawSink;
import ru.mcs.spi.swing.editor.style.ComputedStyle;

/**
 * Сетка таблицы: ширина колонок по тексту, перенос длинных ячеек, горизонтальная прокрутка широких таблиц;
 * редактирование идёт в исходном Markdown.
 */
public final class TableWidget extends AbstractBlockWidget {

    private static final int MIN_LINE_HEIGHT = 18;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SCROLLBAR_HEIGHT = 8;
    private static final int MIN_THUMB = 24;

    private final TableModel table;
    private final TableGrid grid;
    private final List<TableModel.Alignment> alignments;
    private final ComputedStyle style;
    private final Font font;
    private final Font boldFont;
    private final TableGrid.Metrics metrics;
    private final int lineHeight;
    private final String caption;
    private final TableScrollState scroll;
    private Styles styles;

    TableWidget(int start, int end, String source, ComputedStyle style, Font font, String caption) {
        this(start, end, source, style, font, caption, new TableScrollState());
    }

    TableWidget(int start, int end, String source, ComputedStyle style, Font font, String caption,
                TableScrollState scroll) {
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
        this.scroll = scroll;
    }

    /** Цвета из CSS темы: заголовок (th), нечётные/чётные строки тела (tr:nth-child), граница (td); null — по умолчанию. */
    public record Styles(Color header, Color odd, Color even, Color border) {
    }

    public void setStyles(Styles styles) {
        this.styles = styles;
    }

    @Override
    public int height(int width) {
        if (grid == null || table == null) {
            return WidgetText.lineHeight(font) + 16 + captionHeight();
        }
        int content = geometry(width).height();
        if (overflows(width)) {
            content += SCROLLBAR_GAP + SCROLLBAR_HEIGHT + 2;
        }
        return content + captionHeight();
    }

    private int captionHeight() {
        return caption == null ? 0 : WidgetText.lineHeight(font) + 4;
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
        TableGrid.Geometry geometry = geometry(bounds.width);
        int originX = bounds.x - scrollOffset(bounds.width);
        int tableWidth = geometry.width();
        int right = originX + tableWidth;
        int tableHeight = geometry.height();
        sink.clip(bounds.x, bounds.y, bounds.width, tableHeight + 1);
        try {
            for (int row = 0; row < grid.rowCount(); row++) {
                int top = bounds.y + geometry.top(row);
                int height = geometry.top(row + 1) - geometry.top(row);
                Color background = rowBackground(row);
                if (background != null) {
                    sink.fillRect(originX, top, tableWidth, height, background);
                }
                sink.drawLine(originX, top, right, top, 1, border);
                Font cellFont = row == 0 ? boldFont : font;
                for (int column = 0; column < grid.columnCount(); column++) {
                    int left = originX + geometry.edge(column);
                    sink.drawLine(left, top, left, top + height, 1, border);
                    List<TableGrid.Line> lines = geometry.lines(row, column);
                    for (int index = 0; index < lines.size(); index++) {
                        TableGrid.Line line = lines.get(index);
                        int lineTop = top + TableGrid.VERTICAL_PADDING + index * lineHeight;
                        int baseline = lineTop + (lineHeight + WidgetText.baseline(cellFont)) / 2 - 2;
                        WidgetText.draw(sink, line.text(), cellFont, style.color(), originX + line.x(), baseline);
                    }
                }
                sink.drawLine(right, top, right, top + height, 1, border);
            }
            sink.drawLine(originX, bounds.y + tableHeight, right, bounds.y + tableHeight, 1, border);
        } finally {
            sink.unclip();
        }
        Rectangle track = scrollbarTrack(bounds.x, bounds.y, bounds.width);
        if (track != null) {
            Rectangle thumb = scrollbarThumb(bounds.x, bounds.y, bounds.width);
            sink.fillRoundRect(track.x, track.y, track.width, track.height, 4, new Color(128, 128, 128, 40));
            sink.fillRoundRect(thumb.x, thumb.y, thumb.width, thumb.height, 4, new Color(128, 128, 128, 140));
        }
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

    /** Таблица шире области и прокручивается по горизонтали. */
    public boolean overflows(int width) {
        return grid != null && table != null && geometry(width).width() > width;
    }

    public int maxScroll(int width) {
        return grid == null || table == null ? 0 : Math.max(0, geometry(width).width() - width);
    }

    public int scrollOffset(int width) {
        return Math.max(0, Math.min(maxScroll(width), scroll.get(sourceStart())));
    }

    public void setScrollOffset(int offset, int width) {
        scroll.set(sourceStart(), Math.max(0, Math.min(maxScroll(width), offset)));
    }

    public void scrollBy(int delta, int width) {
        setScrollOffset(scrollOffset(width) + delta, width);
    }

    /** Прокручивает так, чтобы каретка была видна; true, если смещение изменилось. */
    public boolean reveal(int offset, int width) {
        if (grid == null || !overflows(width)) {
            return false;
        }
        int current = scrollOffset(width);
        int target = grid.revealScroll(offset, current, width, baseLayout(0, 0, width));
        setScrollOffset(target, width);
        return scrollOffset(width) != current;
    }

    /** Дорожка полосы прокрутки под таблицей; null, если прокрутка не нужна. */
    public Rectangle scrollbarTrack(int x, int y, int width) {
        if (!overflows(width)) {
            return null;
        }
        return new Rectangle(x, y + geometry(width).height() + SCROLLBAR_GAP, width, SCROLLBAR_HEIGHT);
    }

    public Rectangle scrollbarThumb(int x, int y, int width) {
        Rectangle track = scrollbarTrack(x, y, width);
        if (track == null) {
            return null;
        }
        int tableWidth = geometry(width).width();
        int thumbWidth = Math.max(MIN_THUMB, (int) ((long) width * width / tableWidth));
        int span = Math.max(0, width - thumbWidth);
        int max = maxScroll(width);
        int thumbX = max == 0 ? x : x + (int) ((long) span * scrollOffset(width) / max);
        return new Rectangle(thumbX, track.y, thumbWidth, track.height);
    }

    /** Перетаскивание ползунка: {@code grab} — расстояние от левого края ползунка до указателя. */
    public void dragScrollbar(int mouseX, int grab, int x, int width) {
        Rectangle thumb = scrollbarThumb(x, 0, width);
        if (thumb == null) {
            return;
        }
        int span = Math.max(1, width - thumb.width);
        int thumbX = Math.max(0, Math.min(span, mouseX - grab - x));
        setScrollOffset((int) Math.round((double) thumbX * maxScroll(width) / span), width);
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

    private TableGrid.Geometry geometry(int width) {
        return grid.geometry(baseLayout(0, 0, width));
    }

    private TableGrid.Layout baseLayout(int x, int y, int width) {
        return new TableGrid.Layout(x, y, width, lineHeight, metrics, alignments);
    }

    /** Раскладка с учётом горизонтальной прокрутки: координаты ячеек сдвинуты влево на смещение. */
    private TableGrid.Layout layout(int x, int y, int width) {
        return baseLayout(x - scrollOffset(width), y, width);
    }
}
