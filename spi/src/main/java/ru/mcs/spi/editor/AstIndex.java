package ru.mcs.spi.editor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ru.mcs.spi.editor.MarkdownParser.ParseMetrics;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;

/** Неизменяемый снимок flat AST с быстрым поиском top-level блока по UTF-16 offset. */
public final class AstIndex {

    private final long version;
    private final List<BlockSpan> spans;
    private final List<BlockSpan> topLevelBlocks;
    private final ParseMetrics metrics;

    public AstIndex(ParseResult result) {
        Objects.requireNonNull(result, "result");
        version = result.version();
        spans = List.copyOf(result.spans());
        topLevelBlocks = spans.stream().filter(span -> span.depth() == 1).toList();
        metrics = Objects.requireNonNull(result.metrics(), "result.metrics");
    }

    public long version() {
        return version;
    }

    public List<BlockSpan> spans() {
        return spans;
    }

    public List<BlockSpan> topLevelBlocks() {
        return topLevelBlocks;
    }

    public ParseMetrics metrics() {
        return metrics;
    }

    /** Возвращает блок с наибольшим start не правее offset, если offset входит в его source span. */
    public Optional<BlockSpan> blockAt(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        }
        int low = 0;
        int high = topLevelBlocks.size() - 1;
        int candidate = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            BlockSpan span = topLevelBlocks.get(middle);
            if (span.start() <= offset) {
                candidate = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (candidate < 0) {
            return Optional.empty();
        }
        BlockSpan span = topLevelBlocks.get(candidate);
        return offset < span.end() || offset == span.start() && offset == span.end()
                ? Optional.of(span) : Optional.empty();
    }

    /** Все узлы, содержащие offset; порядок соответствует исходному flat AST. */
    public List<BlockSpan> spansAt(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        }
        return spans.stream()
                .filter(span -> span.start() <= offset
                        && (offset < span.end() || offset == span.start() && offset == span.end()))
                .toList();
    }
}
