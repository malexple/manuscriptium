package ru.mcs.markdown.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Мутации Markdown-таблицы; caller заменяет только исходный BlockSpan через DocumentModel.apply. */
public final class TableModel {

    public enum Alignment { LEFT, CENTER, RIGHT }

    private final List<Row> rows;
    private final List<Alignment> alignments;

    private TableModel(List<Row> rows, List<Alignment> alignments) {
        this.rows = rows;
        this.alignments = alignments;
    }

    public static TableModel parse(String source) {
        String[] lines = source.strip().split("\\R");
        if (lines.length < 2) {
            return null;
        }
        List<Cell> separator = row(lines[1], 0);
        if (separator.isEmpty() || separator.stream().noneMatch(cell -> cell.text().matches(":?-{3,}:?"))) {
            return null;
        }
        List<Alignment> alignments = new ArrayList<>();
        for (Cell cell : separator) {
            String value = cell.text();
            alignments.add(value.startsWith(":") && value.endsWith(":") ? Alignment.CENTER
                    : value.endsWith(":") ? Alignment.RIGHT : Alignment.LEFT);
        }
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(row(lines[0], 0)));
        for (int index = 2; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                rows.add(new Row(row(lines[index], 0)));
            }
        }
        int columns = alignments.size();
        if (columns == 0) {
            return null;
        }
        rows.replaceAll(row -> row.resize(columns));
        return new TableModel(rows, alignments);
    }

    public static final int MAX_COLUMNS = 20;
    public static final int MAX_ROWS = 100;

    /** Пустая таблица: первая строка — заголовок, все колонки выровнены по левому краю. */
    public static TableModel blank(int columns, int rows) {
        int columnCount = Math.max(1, Math.min(MAX_COLUMNS, columns));
        int rowCount = Math.max(1, Math.min(MAX_ROWS, rows));
        List<Row> list = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            List<Cell> cells = new ArrayList<>();
            for (int column = 0; column < columnCount; column++) {
                cells.add(new Cell("", 0));
            }
            list.add(new Row(cells));
        }
        List<Alignment> alignments = new ArrayList<>();
        for (int column = 0; column < columnCount; column++) {
            alignments.add(Alignment.LEFT);
        }
        return new TableModel(list, alignments);
    }

    /** Таблица из готовых строк (первая — заголовок); {@code |} в ячейках экранируется. */
    public static TableModel fromRows(List<List<String>> source, List<Alignment> columnAlignments) {
        List<Row> list = new ArrayList<>();
        for (List<String> line : source) {
            List<Cell> cells = new ArrayList<>();
            for (String text : line) {
                cells.add(new Cell(text.replace("|", "\\|"), 0));
            }
            list.add(new Row(cells));
        }
        return new TableModel(list, new ArrayList<>(columnAlignments));
    }

    /**
     * Строки тела, отсортированные по колонке (заголовок остаётся первым). Числа сравниваются как числа и идут
     * раньше текста, текст — без учёта регистра, пустые ячейки всегда в конце; сортировка стабильна.
     */
    public TableModel sortBy(int column, boolean ascending) {
        if (column < 0 || column >= columnCount() || rows.size() < 3) {
            return this;
        }
        java.text.Collator collator = java.text.Collator.getInstance(Locale.getDefault());
        collator.setStrength(java.text.Collator.SECONDARY);
        List<Integer> order = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            order.add(index);
        }
        order.sort((first, second) -> {
            String left = cell(first, column).strip();
            String right = cell(second, column).strip();
            if (left.isEmpty() || right.isEmpty()) {
                return left.isEmpty() == right.isEmpty() ? 0 : left.isEmpty() ? 1 : -1;
            }
            Double leftNumber = number(left);
            Double rightNumber = number(right);
            int result;
            if (leftNumber != null && rightNumber != null) {
                result = Double.compare(leftNumber, rightNumber);
            } else if (leftNumber != null || rightNumber != null) {
                result = leftNumber != null ? -1 : 1;
            } else {
                result = collator.compare(left, right);
            }
            return ascending ? result : -result;
        });
        List<Row> sorted = new ArrayList<>();
        sorted.add(rows.get(0));
        for (int index : order) {
            sorted.add(rows.get(index));
        }
        return new TableModel(sorted, new ArrayList<>(alignments));
    }

    private static Double number(String text) {
        String value = text.replaceAll("[\\s\\u00a0]", "").replace(',', '.');
        if (value.endsWith("%")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.matches("[-+]?\\d+(\\.\\d+)?") ? Double.valueOf(value) : null;
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return alignments.size();
    }

    public String cell(int row, int column) {
        return rows.get(row).cells.get(column).text();
    }

    public Alignment alignment(int column) {
        return alignments.get(column);
    }

    public TableModel setCell(int row, int column, String value) {
        List<Row> copy = rows();
        List<Cell> cells = copy.get(row).mutableCells();
        cells.set(column, new Cell(value == null ? "" : value.replace("|", "\\|"), 0));
        copy.set(row, new Row(cells));
        return new TableModel(copy, alignments);
    }

    public TableModel addRow(int after) {
        List<Row> copy = rows();
        List<Cell> cells = new ArrayList<>();
        for (int column = 0; column < columnCount(); column++) {
            cells.add(new Cell("", 0));
        }
        copy.add(Math.max(1, Math.min(copy.size(), after + 1)), new Row(cells));
        return new TableModel(copy, alignments);
    }

    public TableModel removeRow(int row) {
        if (rows.size() <= 1 || row == 0) {
            return null;
        }
        List<Row> copy = rows();
        copy.remove(row);
        return new TableModel(copy, alignments);
    }

    public TableModel addColumn(int after) {
        List<Row> copy = new ArrayList<>();
        int index = Math.max(-1, Math.min(columnCount() - 1, after)) + 1;
        for (Row row : rows) {
            List<Cell> cells = row.mutableCells();
            cells.add(index, new Cell("", 0));
            copy.add(new Row(cells));
        }
        List<Alignment> columns = new ArrayList<>(alignments);
        columns.add(index, Alignment.LEFT);
        return new TableModel(copy, columns);
    }

    public TableModel removeColumn(int column) {
        if (columnCount() <= 1) {
            return null;
        }
        List<Row> copy = new ArrayList<>();
        for (Row row : rows) {
            List<Cell> cells = row.mutableCells();
            cells.remove(column);
            copy.add(new Row(cells));
        }
        List<Alignment> columns = new ArrayList<>(alignments);
        columns.remove(column);
        return new TableModel(copy, columns);
    }

    public TableModel align(int column, Alignment alignment) {
        List<Alignment> columns = new ArrayList<>(alignments);
        columns.set(column, Objects.requireNonNull(alignment, "alignment"));
        return new TableModel(rows(), columns);
    }

    /** Новый размер: содержимое и выравнивание сохраняются, недостающие ячейки пустые, лишние отбрасываются. */
    public TableModel resize(int columns, int rows) {
        int columnCount = Math.max(1, Math.min(MAX_COLUMNS, columns));
        int rowCount = Math.max(1, Math.min(MAX_ROWS, rows));
        List<Row> copy = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            List<Cell> cells = new ArrayList<>();
            for (int column = 0; column < columnCount; column++) {
                boolean exists = row < this.rows.size() && column < alignments.size();
                cells.add(exists ? this.rows.get(row).cells.get(column) : new Cell("", 0));
            }
            copy.add(new Row(cells));
        }
        List<Alignment> columnsAlignment = new ArrayList<>();
        for (int column = 0; column < columnCount; column++) {
            columnsAlignment.add(column < alignments.size() ? alignments.get(column) : Alignment.LEFT);
        }
        return new TableModel(copy, columnsAlignment);
    }

    /** Исходник с выровненными колонками: ширина колонки равна самой длинной ячейке. */
    public String prettySource() {
        int columns = columnCount();
        int[] widths = new int[columns];
        for (int column = 0; column < columns; column++) {
            widths[column] = alignments.get(column) == Alignment.CENTER ? 5 : 4;
            for (Row row : rows) {
                widths[column] = Math.max(widths[column], row.cells.get(column).text().length());
            }
        }
        StringBuilder result = new StringBuilder();
        appendPadded(result, rows.getFirst().cells, widths, true);
        result.append('\n');
        List<Cell> separator = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            int width = widths[column];
            separator.add(new Cell(switch (alignments.get(column)) {
                case LEFT -> ":" + "-".repeat(width - 1);
                case CENTER -> ":" + "-".repeat(width - 2) + ":";
                case RIGHT -> "-".repeat(width - 1) + ":";
            }, 0));
        }
        appendPadded(result, separator, widths, false);
        for (int row = 1; row < rows.size(); row++) {
            result.append('\n');
            appendPadded(result, rows.get(row).cells, widths, true);
        }
        return result.toString();
    }

    private void appendPadded(StringBuilder result, List<Cell> cells, int[] widths, boolean pad) {
        result.append('|');
        for (int column = 0; column < cells.size(); column++) {
            String text = cells.get(column).text();
            int gap = pad ? Math.max(0, widths[column] - text.length()) : 0;
            String aligned = switch (alignments.get(column)) {
                case LEFT -> text + " ".repeat(gap);
                case RIGHT -> " ".repeat(gap) + text;
                case CENTER -> " ".repeat(gap / 2) + text + " ".repeat(gap - gap / 2);
            };
            result.append(' ').append(aligned).append(" |");
        }
    }

    public String source() {
        StringBuilder result = new StringBuilder();
        appendRow(result, rows.getFirst().cells);
        result.append('\n');
        List<Cell> separator = new ArrayList<>();
        for (Alignment alignment : alignments) {
            separator.add(new Cell(switch (alignment) {
                case LEFT -> ":---";
                case CENTER -> ":---:";
                case RIGHT -> "---:";
            }, 0));
        }
        appendRow(result, separator);
        for (int row = 1; row < rows.size(); row++) {
            result.append('\n');
            appendRow(result, rows.get(row).cells);
        }
        return result.toString();
    }

    public int rowAtSourceLine(int line) {
        return Math.max(0, Math.min(rowCount() - 1, line == 0 ? 0 : line - 1));
    }

    private List<Row> rows() {
        return new ArrayList<>(rows);
    }

    private static void appendRow(StringBuilder result, List<Cell> cells) {
        result.append("|");
        for (Cell cell : cells) {
            result.append(' ').append(cell.text()).append(" |");
        }
    }

    private static List<Cell> row(String line, int offset) {
        String value = line.strip();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|") && !value.endsWith("\\|")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            return List.of();
        }
        List<Cell> cells = new ArrayList<>();
        int start = 0;
        boolean escaped = false;
        boolean code = false;
        for (int index = 0; index <= value.length(); index++) {
            char character = index < value.length() ? value.charAt(index) : '|';
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
            } else if (character == '`') {
                code = !code;
            } else if (character == '|' && !code) {
                cells.add(new Cell(value.substring(start, index).strip(), offset + start));
                start = index + 1;
            }
        }
        return Collections.unmodifiableList(cells);
    }

    public record Cell(String text, int sourceOffset) {
    }

    private record Row(List<Cell> cells) {
        private Row {
            cells = List.copyOf(cells);
        }

        private Row resize(int columns) {
            List<Cell> result = mutableCells();
            while (result.size() < columns) {
                result.add(new Cell("", 0));
            }
            return new Row(result.subList(0, columns));
        }

        private List<Cell> mutableCells() {
            return new ArrayList<>(cells);
        }
    }
}
