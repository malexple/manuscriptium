package ru.mcs.markdown.editor.view;

import java.util.function.UnaryOperator;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.Replace;
import ru.mcs.markdown.widget.TableModel;
import ru.mcs.markdown.api.AstIndex;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;

/** Позиционные и структурные операции таблицы над её исходным span, без сериализации всего документа. */
final class TableController {

    private TableController() {
    }

    static boolean navigate(MarkdownCanvas canvas, int direction) {
        Location location = location(canvas);
        if (location == null) {
            return false;
        }
        int row = location.row;
        int column = location.column + direction;
        if (column >= location.model.columnCount()) {
            column = 0;
            row++;
        } else if (column < 0) {
            column = location.model.columnCount() - 1;
            row--;
        }
        row = Math.max(0, Math.min(location.model.rowCount() - 1, row));
        int offset = cellOffset(location, row, column);
        canvas.setCaretState(offset, offset);
        canvas.ensureCaretVisible();
        canvas.repaint();
        return true;
    }

    static boolean edit(MarkdownCanvas canvas, UnaryOperator<TableModel> operation) {
        Location location = location(canvas);
        if (location == null) {
            return false;
        }
        TableModel changed = operation.apply(location.model);
        if (changed == null) {
            return true;
        }
        String replacement = changed.source();
        DocumentModel document = canvas.document();
        document.apply(Replace.of(location.start, location.end, replacement));
        int offset = Math.min(document.length(), cellOffset(new Location(location.start,
                location.start + replacement.length(), replacement, changed, location.row, location.column),
                location.row, location.column));
        canvas.setCaretState(offset, offset);
        return true;
    }

    static String cell(MarkdownCanvas canvas) {
        Location location = location(canvas);
        return location == null ? null : location.model.cell(location.row, location.column);
    }

    static Location location(MarkdownCanvas canvas) {
        DocumentModel document = canvas.document();
        AstIndex ast = canvas.astIndex();
        if (document == null || ast == null || ast.version() != document.version()) {
            return null;
        }
        int caret = canvas.caretOffset();
        for (BlockSpan span : ast.topLevelBlocks()) {
            if (span.kind() == BlockKind.TABLE && caret >= span.start() && caret <= span.end()) {
                String raw = document.text().toString();
                int end = span.end();
                while (end > span.start() && (raw.charAt(end - 1) == '\n' || raw.charAt(end - 1) == '\r')) {
                    end--;
                }
                TableModel model = TableModel.parse(raw.substring(span.start(), end));
                if (model == null) {
                    return null;
                }
                int line = 0;
                int lineStart = span.start();
                for (int index = span.start(); index < caret && index < end; index++) {
                    if (raw.charAt(index) == '\n') {
                        line++;
                        lineStart = index + 1;
                    }
                }
                int row = Math.max(0, Math.min(model.rowCount() - 1, line <= 1 ? 0 : line - 1));
                int pipes = 0;
                boolean escaped = false;
                for (int index = lineStart; index < caret && index < end; index++) {
                    char character = raw.charAt(index);
                    if (escaped) {
                        escaped = false;
                    } else if (character == '\\') {
                        escaped = true;
                    } else if (character == '|') {
                        pipes++;
                    }
                }
                int column = Math.max(0, Math.min(model.columnCount() - 1, Math.max(0, pipes - 1)));
                return new Location(span.start(), end, raw.substring(span.start(), end), model, row, column);
            }
        }
        return null;
    }

    private static int cellOffset(Location location, int row, int column) {
        String source = location.source;
        int line = row == 0 ? 0 : row + 1;
        int start = 0;
        for (int index = 0; index < line; index++) {
            int newline = source.indexOf('\n', start);
            if (newline < 0) {
                return location.start;
            }
            start = newline + 1;
        }
        int end = source.indexOf('\n', start);
        if (end < 0) {
            end = source.length();
        }
        String text = source.substring(start, end);
        int cursor = text.startsWith("|") ? 1 : 0;
        for (int index = 0; index < column; index++) {
            int pipe = text.indexOf('|', cursor);
            if (pipe < 0) {
                return location.start + end;
            }
            cursor = pipe + 1;
        }
        while (cursor < text.length() && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t')) {
            cursor++;
        }
        return location.start + start + cursor;
    }

    record Location(int start, int end, String source, TableModel model, int row, int column) {
    }
}
