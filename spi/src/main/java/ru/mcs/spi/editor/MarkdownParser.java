package ru.mcs.spi.editor;

import java.util.List;

/**
 * Сервис Markdown AST. Реализация поставляется отдельным плагином и не обязана быть потокобезопасной:
 * вызовы сериализуются общим executor, результаты неизменяемы, а reparse использует предыдущий snapshot.
 */
public interface MarkdownParser {

    ParseResult parse(CharSequence text, long version);

    /**
     * Перепарсивает dirty-окно итогового текста. Previous должен быть результатом этой же реализации;
     * иначе реализация выполняет полный parse. Метрика всегда сообщает фактическое число блоков.
     */
    ParseResult reparse(ParseResult previous, CharSequence text, long version, Change change);

    record Change(int start, int oldLength, int newLength) {
        public Change {
            if (start < 0 || oldLength < 0 || newLength < 0) {
                throw new IllegalArgumentException("Invalid Markdown change");
            }
        }
    }

    interface ParseResult {
        long version();

        List<BlockSpan> spans();

        ParseMetrics metrics();
    }

    record ParseMetrics(int parsedBlocks, int reusedBlocks, boolean fullParse) {
        public ParseMetrics {
            if (parsedBlocks < 0 || reusedBlocks < 0) {
                throw new IllegalArgumentException("Negative parse metric");
            }
        }
    }
}
