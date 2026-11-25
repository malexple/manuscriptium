package ru.mcs.markdown.widget;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Source-координаты ячеек Markdown-таблицы и их экранная геометрия: ширина колонок как у CSS auto-table,
 * перенос длинного текста по словам, каретка, клик и выделение внутри ячеек.
 */
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

    /** @param lineHeight высота одной строки текста в ячейке; высота строки таблицы = строки * lineHeight + отступы */
    public record Layout(int x, int y, int width, int lineHeight, Metrics metrics,
                         List<TableModel.Alignment> alignments) {
    }

    /** Строка текста внутри ячейки: диапазон исходника, позиция и ширина в пикселях относительно таблицы. */
    public record Line(int start, int end, int x, int width, String text) {
    }

    /** Размеры таблицы относительно её левого верхнего угла. */
    public static final class Geometry {
        private final int[] edges;
        private final int[] tops;
        private final List<List<List<Line>>> lines;

        private Geometry(int[] edges, int[] tops, List<List<List<Line>>> lines) {
            this.edges = edges;
            this.tops = tops;
            this.lines = lines;
        }

        public int edge(int column) {
            return edges[column];
        }

        public int top(int row) {
            return tops[row];
        }

        public int height() {
            return tops[tops.length - 1];
        }

        public List<Line> lines(int row, int column) {
            return lines.get(row).get(column);
        }
    }

    public static final int PADDING = 8;
    public static final int VERTICAL_PADDING = 6;

    private final String source;
    private final int base;
    private final List<List<Cell>> rows;
    private final int[] lineStarts;
    private final int columns;

    private Geometry cached;
    private int cachedWidth = -1;
    private int cachedLineHeight;
    private Metrics cachedMetrics;
    private List<TableModel.Alignment> cachedAlignments;

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

    /** Высота таблицы при заданной ширине; результат кешируется для тех же параметров раскладки. */
    public int height(Layout layout) {
        return geometry(layout).height();
    }

    public Geometry geometry(Layout layout) {
        if (cached == null || cachedWidth != layout.width() || cachedLineHeight != layout.lineHeight()
                || cachedMetrics != layout.metrics() || cachedAlignments != layout.alignments()) {
            cached = compute(layout);
            cachedWidth = layout.width();
            cachedLineHeight = layout.lineHeight();
            cachedMetrics = layout.metrics();
            cachedAlignments = layout.alignments();
        }
        return cached;
    }

    public Rectangle caretRect(int offset, Layout layout) {
        Geometry geometry = geometry(layout);
        Cell cell = cellAt(offset);
        List<Line> lines = geometry.lines(cell.row(), cell.column());
        int index = lineIndex(lines, offset);
        return new Rectangle(caretX(cell, lines, offset, layout),
                layout.y() + geometry.top(cell.row()) + VERTICAL_PADDING + index * layout.lineHeight(),
                1, layout.lineHeight());
    }

    /** Ближайшая к точке позиция каретки; точки за пределами таблицы прижимаются к ближайшей ячейке. */
    public int hitOffset(Point point, Layout layout) {
        Geometry geometry = geometry(layout);
        int y = point.y - layout.y();
        int x = point.x - layout.x();
        int row = rows.size() - 1;
        for (int index = 0; index < rows.size(); index++) {
            if (y < geometry.top(index + 1)) {
                row = index;
                break;
            }
        }
        int column = columns - 1;
        for (int index = 0; index < columns; index++) {
            if (x < geometry.edge(index + 1)) {
                column = index;
                break;
            }
        }
        Cell cell = cell(row, column);
        List<Line> lines = geometry.lines(row, column);
        int lineIndex = Math.max(0, Math.min(lines.size() - 1,
                Math.floorDiv(y - geometry.top(row) - VERTICAL_PADDING, layout.lineHeight())));
        Line line = lines.get(lineIndex);
        int best = line.start();
        int bestDistance = Integer.MAX_VALUE;
        for (int offset = line.start(); offset <= line.end(); offset++) {
            if (insideEscape(offset)) {
                continue;
            }
            int distance = Math.abs(caretX(cell, lines, offset, layout) - point.x);
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
        Geometry geometry = geometry(layout);
        for (List<Cell> row : rows) {
            for (Cell cell : row) {
                List<Line> lines = geometry.lines(cell.row(), cell.column());
                for (int index = 0; index < lines.size(); index++) {
                    Line line = lines.get(index);
                    int from = Math.max(start, line.start());
                    int to = Math.min(end, line.end());
                    if (from >= to) {
                        continue;
                    }
                    int x1 = caretX(cell, lines, from, layout);
                    int x2 = caretX(cell, lines, to, layout);
                    result.add(new Rectangle(x1,
                            layout.y() + geometry.top(cell.row()) + VERTICAL_PADDING + index * layout.lineHeight(),
                            Math.max(1, x2 - x1), layout.lineHeight()));
                }
            }
        }
        return result;
    }

    private int caretX(Cell cell, List<Line> lines, int offset, Layout layout) {
        int index = lineIndex(lines, offset);
        Line line = lines.get(index);
        int limit = index == lines.size() - 1 ? cell.rawEnd() : line.end();
        int clamped = Math.max(line.start(), Math.min(limit, offset));
        return layout.x() + line.x() + layout.metrics().width(display(text(line.start(), clamped)), cell.row() == 0);
    }

    private static int lineIndex(List<Line> lines, int offset) {
        int index = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).start() <= offset) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private Geometry compute(Layout layout) {
        int[] widths = columnWidths(layout);
        int[] edges = new int[columns + 1];
        for (int column = 0; column < columns; column++) {
            edges[column + 1] = edges[column] + widths[column];
        }
        List<List<List<Line>>> all = new ArrayList<>();
        int[] tops = new int[rows.size() + 1];
        for (int row = 0; row < rows.size(); row++) {
            List<List<Line>> rowLines = new ArrayList<>();
            int maxLines = 1;
            for (int column = 0; column < columns; column++) {
                List<Line> lines = wrap(rows.get(row).get(column), widths[column], edges[column], layout);
                rowLines.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            all.add(rowLines);
            tops[row + 1] = tops[row] + maxLines * layout.lineHeight() + 2 * VERTICAL_PADDING;
        }
        return new Geometry(edges, tops, all);
    }

    /**
     * Правила как у CSS auto-table: если всё помещается в одну строку, лишняя ширина делится пропорционально
     * естественной ширине колонок; иначе колонки сжимаются между «самое длинное слово» и «весь текст»;
     * когда нет места даже для слов, ширины пропорциональны словам, а слова переносятся по символам.
     */
    private int[] columnWidths(Layout layout) {
        int total = Math.max(columns, layout.width());
        long[] min = new long[columns];
        long[] max = new long[columns];
        for (int column = 0; column < columns; column++) {
            min[column] = 2L * PADDING;
            max[column] = 2L * PADDING;
        }
        for (List<Cell> row : rows) {
            for (Cell cell : row) {
                boolean header = cell.row() == 0;
                String text = display(text(cell.start(), cell.end()));
                long full = layout.metrics().width(text, header) + 2L * PADDING;
                long longest = 0;
                for (String word : text.split(" ")) {
                    if (!word.isEmpty()) {
                        longest = Math.max(longest, layout.metrics().width(word, header));
                    }
                }
                longest += 2L * PADDING;
                max[cell.column()] = Math.max(max[cell.column()], full);
                min[cell.column()] = Math.max(min[cell.column()], Math.min(longest, full));
            }
        }
        long sumMax = 0;
        long sumMin = 0;
        for (int column = 0; column < columns; column++) {
            sumMax += max[column];
            sumMin += min[column];
        }
        int[] widths = new int[columns];
        for (int column = 0; column < columns; column++) {
            if (sumMax <= total) {
                widths[column] = (int) (max[column] + (total - sumMax) * max[column] / sumMax);
            } else if (sumMin >= total) {
                widths[column] = (int) Math.max(1, total * min[column] / sumMin);
            } else {
                widths[column] = (int) (min[column] + (total - sumMin) * (max[column] - min[column])
                        / (sumMax - sumMin));
            }
        }
        int sum = 0;
        for (int width : widths) {
            sum += width;
        }
        widths[columns - 1] = Math.max(1, widths[columns - 1] + total - sum);
        return widths;
    }

    private List<Line> wrap(Cell cell, int columnWidth, int left, Layout layout) {
        boolean header = cell.row() == 0;
        String text = text(cell.start(), cell.end());
        int maxWidth = Math.max(1, columnWidth - 2 * PADDING);
        Metrics metrics = layout.metrics();
        int space = metrics.width(" ", header);
        List<int[]> ranges = new ArrayList<>();
        int length = text.length();
        int lineStart = -1;
        int lineEnd = -1;
        int lineWidth = 0;
        int index = 0;
        while (index < length) {
            int gapStart = index;
            while (index < length && text.charAt(index) == ' ') {
                index++;
            }
            int gap = index - gapStart;
            if (index >= length) {
                break;
            }
            int wordStart = index;
            while (index < length && text.charAt(index) != ' ') {
                index++;
            }
            int wordWidth = metrics.width(display(text.substring(wordStart, index)), header);
            if (lineStart >= 0 && lineWidth + gap * space + wordWidth <= maxWidth) {
                lineEnd = index;
                lineWidth += gap * space + wordWidth;
                continue;
            }
            if (lineStart >= 0) {
                ranges.add(new int[]{lineStart, lineEnd});
            }
            lineStart = wordStart;
            lineEnd = index;
            lineWidth = wordWidth;
            if (wordWidth > maxWidth) {
                int segmentStart = wordStart;
                int segmentWidth = 0;
                for (int position = wordStart; position < index; position++) {
                    int charWidth = metrics.width(display(text.substring(position, position + 1)), header);
                    if (segmentWidth + charWidth > maxWidth && position > segmentStart) {
                        ranges.add(new int[]{segmentStart, position});
                        segmentStart = position;
                        segmentWidth = 0;
                    }
                    segmentWidth += charWidth;
                }
                lineStart = segmentStart;
                lineEnd = index;
                lineWidth = segmentWidth;
            }
        }
        if (lineStart >= 0) {
            ranges.add(new int[]{lineStart, lineEnd});
        }
        if (ranges.isEmpty()) {
            ranges.add(new int[]{0, 0});
        }
        TableModel.Alignment alignment = cell.column() < layout.alignments().size()
                ? layout.alignments().get(cell.column()) : TableModel.Alignment.LEFT;
        List<Line> result = new ArrayList<>();
        for (int[] range : ranges) {
            String shown = display(text.substring(range[0], range[1]));
            int width = metrics.width(shown, header);
            int x = switch (alignment) {
                case LEFT -> left + PADDING;
                case CENTER -> left + Math.max(PADDING, (columnWidth - width) / 2);
                case RIGHT -> Math.max(left + PADDING, left + columnWidth - PADDING - width);
            };
            result.add(new Line(cell.start() + range[0], cell.start() + range[1], x, width, shown));
        }
        return result;
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
