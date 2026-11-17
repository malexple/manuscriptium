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
