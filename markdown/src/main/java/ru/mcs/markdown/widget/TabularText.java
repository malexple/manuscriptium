package ru.mcs.markdown.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Разбор табличного текста из буфера обмена (TSV, как копирует Excel) в {@link TableModel}. */
public final class TabularText {

    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d[\\d.,]*%?");

    private TabularText() {
    }

    /** @return таблицу или {@code null}, если текст не похож на табличные данные (нет табуляций или одна колонка) */
    public static TableModel toTable(String text) {
        List<List<String>> rows = parse(text);
        if (rows == null) {
            return null;
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> normalized = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (int column = 0; column < columns; column++) {
                cells.add(column < row.size() ? clean(row.get(column)) : "");
            }
            normalized.add(cells);
        }
        List<TableModel.Alignment> alignments = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            alignments.add(numeric(normalized, column) ? TableModel.Alignment.RIGHT : TableModel.Alignment.LEFT);
        }
        return TableModel.fromRows(normalized, alignments);
    }

    /** Строки и ячейки; ячейки в кавычках могут содержать табуляции, переводы строк и удвоенные кавычки. */
    static List<List<String>> parse(String text) {
        if (text == null || text.indexOf('\t') < 0) {
            return null;
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(character);
                }
            } else if (character == '"' && cell.length() == 0) {
                quoted = true;
            } else if (character == '\t') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (character == '\n' || character == '\r') {
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(character);
            }
        }
        if (quoted) {
            return null;
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        while (!rows.isEmpty() && rows.getLast().stream().allMatch(String::isEmpty)) {
            rows.removeLast();
        }
        boolean wide = rows.stream().anyMatch(candidate -> candidate.size() >= 2);
        return rows.isEmpty() || !wide ? null : rows;
    }

    private static String clean(String cell) {
        return cell.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>").replace('\t', ' ').strip();
    }

    private static boolean numeric(List<List<String>> rows, int column) {
        boolean any = false;
        for (int row = 1; row < rows.size(); row++) {
            String value = rows.get(row).get(column).replaceAll("[\\s\\u00a0]", "");
            if (value.isEmpty()) {
                continue;
            }
            if (!NUMBER.matcher(value).matches()) {
                return false;
            }
            any = true;
        }
        return any;
    }
}
