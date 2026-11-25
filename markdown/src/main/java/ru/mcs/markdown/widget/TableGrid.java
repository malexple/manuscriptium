package ru.mcs.markdown.widget;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Source-координаты ячеек Markdown-таблицы и их экранная геометрия: каретка, клик, выделение. */
public final class TableGrid {

    /**
     * @param rawStart начало области между разделителями (с пробелами), абсолютный offset
     * @param rawEnd   конец области между разделителями
     * @param start    начало текста ячейки без пробелов; у пустой ячейки равно end
     * @param end      конец текста ячейки
     */
    public record Cell(int row, int column, int rawStart, int rawEnd, int start, int end) {
    }

    public interface Metrics {
        int width(String text, boolean header);
    }

    public record Layout(int x, int y, int width, int rowHeight, Metrics metrics,
                         List<TableModel.Alignment> alignments) {
    }

    private static final int PADDING = 8;
    private static final int CARET_INSET = 4;

    private final String source;
    private final int base;
    private final List<List<Cell>> rows;
    private final int[] lineStarts;
    private final int columns;

    private TableGrid(String source, int base, List<List<Cell>> rows, int[] lineStarts, int columns) {
        this.source = source;
        this.base = base;
        this.rows = rows;
        this.lineStarts = lineStarts;
        this.columns = columns;
    }

    /** @param base абсолютный offset первого символа {@code source} в документе */
    public static TableGrid parse(String source, int base) {
        List<int[]> lines = new ArrayList<>();
        int from = 0;
        while (from <= source.length()) {
            int newline = source.indexOf('\n', from);
            int end = newline < 0 ? source.length() : newline;
            int trimmed = end > from && source.charAt(end - 1) == '\r' ? end - 1 : end;
            lines.add(new int[]{from, trimmed});
            if (newline < 0) {
                break;
            }
            from = newline + 1;
        }
        if (lines.size() < 2) {
            return null;
        }
        int columns = ranges(source, lines.get(1)[0], lines.get(1)[1]).size();
        if (columns == 0) {
            return null;
        }
        List<List<Cell>> rows = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (index == 1) {
                continue;
            }
            int[] line = lines.get(index);
            if (index > 1 && source.substring(line[0], line[1]).isBlank()) {
                continue;
            }
            List<int[]> ranges = ranges(source, line[0], line[1]);
            int row = rows.size();
            List<Cell> cells = new ArrayList<>();
            for (int column = 0; column < columns; column++) {
                if (column < ranges.size()) {
                    cells.add(cell(source, base, row, column, ranges.get(column)));
                } else {
                    int end = base + line[1];
                    cells.add(new Cell(row, column, end, end, end, end));
                }
            }
            rows.add(cells);
            starts.add(base + line[0]);
        }
        int[] lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        return new TableGrid(source, base, rows, lineStarts, columns);
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return columns;
    }

    public Cell cell(int row, int column) {
        return rows.get(row).get(column);
    }

    /** Ячейка, которой принадлежит offset; разделитель относится к ячейке слева, хвост строки — к последней. */
    public Cell cellAt(int offset) {
        int row = 0;
        for (int index = 0; index < lineStarts.length; index++) {
            if (lineStarts[index] <= offset) {
                row = index;
            } else {
                break;
            }
        }
        List<Cell> cells = rows.get(row);
        for (Cell cell : cells) {
            if (offset <= cell.rawEnd()) {
                return cell;
            }
        }
        return cells.get(cells.size() - 1);
    }

    /** Допустимая позиция каретки в ячейке: от начала текста до конца области, включая хвостовые пробелы. */
    public int clamp(int offset) {
        Cell cell = cellAt(offset);
        return Math.max(cell.start(), Math.min(cell.rawEnd(), offset));
    }

    /** Шаг влево: внутри ячейки на символ, на границе — конец предыдущей ячейки (в начале таблицы без изменений). */
    public int previous(int offset) {
        Cell cell = cellAt(offset);
        int clamped = clamp(offset);
        if (clamped > cell.start()) {
            return skipEscape(clamped - 1, -1);
        }
        if (cell.column() > 0) {
            return cell(cell.row(), cell.column() - 1).end();
        }
        if (cell.row() > 0) {
            return cell(cell.row() - 1, columns - 1).end();
        }
        return offset;
    }

    public int next(int offset) {
        Cell cell = cellAt(offset);
        int clamped = clamp(offset);
        if (clamped < cell.end()) {
            return skipEscape(clamped + 1, 1);
        }
        if (cell.column() + 1 < columns) {
            return cell(cell.row(), cell.column() + 1).start();
        }
        if (cell.row() + 1 < rows.size()) {
            return cell(cell.row() + 1, 0).start();
        }
        return offset;
    }

    public Rectangle caretRect(int offset, Layout layout) {
        int[] edges = edges(layout);
        Cell cell = cellAt(offset);
        return new Rectangle(caretX(cell, offset, edges, layout),
                layout.y() + cell.row() * layout.rowHeight() + CARET_INSET, 1,
                layout.rowHeight() - 2 * CARET_INSET);
    }

    /** Ближайшая к точке позиция каретки; точки за пределами таблицы прижимаются к ближайшей ячейке. */
    public int hitOffset(Point point, Layout layout) {
        int row = Math.max(0, Math.min(rows.size() - 1,
                Math.floorDiv(point.y - layout.y(), layout.rowHeight())));
        int[] edges = edges(layout);
        int column = columns - 1;
        for (int index = 0; index < columns; index++) {
            if (point.x < edges[index + 1]) {
                column = index;
                break;
            }
        }
        Cell cell = cell(row, column);
        int best = cell.start();
        int bestDistance = Integer.MAX_VALUE;
        for (int offset = cell.start(); offset <= cell.end(); offset++) {
            if (insideEscape(offset)) {
                continue;
            }
            int distance = Math.abs(caretX(cell, offset, edges, layout) - point.x);
            if (distance < bestDistance) {
                best = offset;
                bestDistance = distance;
            }
        }
        return best;
    }

    public List<Rectangle> selectionRects(int start, int end, Layout layout) {
        List<Rectangle> result = new ArrayList<>();
        if (start >= end) {
            return result;
        }
        int[] edges = edges(layout);
        for (List<Cell> row : rows) {
            for (Cell cell : row) {
                int from = Math.max(start, cell.start());
                int to = Math.min(end, cell.end());
                if (from >= to) {
                    continue;
                }
                int x1 = caretX(cell, from, edges, layout);
                int x2 = caretX(cell, to, edges, layout);
                result.add(new Rectangle(x1, layout.y() + cell.row() * layout.rowHeight(),
                        Math.max(1, x2 - x1), layout.rowHeight()));
            }
        }
        return result;
    }

    private int caretX(Cell cell, int offset, int[] edges, Layout layout) {
        int clamped = Math.max(cell.start(), Math.min(cell.rawEnd(), offset));
        return textX(cell, edges, layout)
                + layout.metrics().width(display(text(cell.start(), clamped)), cell.row() == 0);
    }

    private int textX(Cell cell, int[] edges, Layout layout) {
        int left = edges[cell.column()];
        int right = edges[cell.column() + 1];
        int textWidth = layout.metrics().width(display(text(cell.start(), cell.end())), cell.row() == 0);
        TableModel.Alignment alignment = cell.column() < layout.alignments().size()
                ? layout.alignments().get(cell.column()) : TableModel.Alignment.LEFT;
        return switch (alignment) {
            case LEFT -> left + PADDING;
            case CENTER -> left + Math.max(PADDING, (right - left - textWidth) / 2);
            case RIGHT -> Math.max(left + PADDING, right - textWidth - PADDING);
        };
    }

    /** Те же границы колонок, что при рисовании TableWidget. */
    private int[] edges(Layout layout) {
        int[] edges = new int[columns + 1];
        edges[0] = layout.x();
        for (int column = 0; column < columns; column++) {
            edges[column + 1] = column == columns - 1 ? layout.x() + layout.width()
                    : layout.x() + Math.round((float) layout.width() * (column + 1) / columns);
        }
        return edges;
    }

    /** Позиция между {@code \} и {@code |} экранированного разделителя не является позицией каретки. */
    private boolean insideEscape(int offset) {
        int index = offset - base;
        return index > 0 && index < source.length() && source.charAt(index) == '|'
                && source.charAt(index - 1) == '\\';
    }

    private int skipEscape(int offset, int direction) {
        return insideEscape(offset) ? offset + direction : offset;
    }

    private String text(int start, int end) {
        int from = Math.max(0, Math.min(source.length(), start - base));
        int to = Math.max(from, Math.min(source.length(), end - base));
        return source.substring(from, to);
    }

    private static String display(String text) {
        return text.replace("\\|", "|");
    }

    private static Cell cell(String source, int base, int row, int column, int[] range) {
        int rawStart = range[0];
        int rawEnd = range[1];
        int start = rawStart;
        while (start < rawEnd && Character.isWhitespace(source.charAt(start))) {
            start++;
        }
        int end = rawEnd;
        while (end > start && Character.isWhitespace(source.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            start = rawStart < rawEnd && source.charAt(rawStart) == ' ' ? rawStart + 1 : rawStart;
            end = start;
        }
        return new Cell(row, column, base + rawStart, base + rawEnd, base + start, base + end);
    }

    /** Границы ячеек строки в локальных offset; правила совпадают с TableModel: экранирование и code span. */
    private static List<int[]> ranges(String source, int from, int to) {
        int begin = from;
        while (begin < to && Character.isWhitespace(source.charAt(begin))) {
            begin++;
        }
        int last = to;
        while (last > begin && Character.isWhitespace(source.charAt(last - 1))) {
            last--;
        }
        if (begin >= last) {
            return List.of();
        }
        int cellStart = source.charAt(begin) == '|' ? begin + 1 : begin;
        int limit = last;
        if (limit - 1 >= cellStart && source.charAt(limit - 1) == '|'
                && !(limit - 2 >= begin && source.charAt(limit - 2) == '\\')) {
            limit--;
        }
        if (source.substring(Math.min(cellStart, limit), limit).isBlank()) {
            return List.of();
        }
        List<int[]> result = new ArrayList<>();
        boolean escaped = false;
        boolean code = false;
        int start = cellStart;
        for (int index = cellStart; index <= limit; index++) {
            if (index == limit) {
                result.add(new int[]{start, index});
                break;
            }
            char character = source.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '`') {
                code = !code;
            } else if (character == '|' && !code) {
                result.add(new int[]{start, index});
                start = index + 1;
            }
        }
        return result;
    }
}
