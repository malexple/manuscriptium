package ru.mcs.plugin.mdeditor;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser;

class TestMarkdownParser implements MarkdownParser {

    static final Executor EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "test-markdown-parser");
        thread.setDaemon(true);
        return thread;
    });

    final AtomicInteger fullParses = new AtomicInteger();
    final AtomicInteger incrementalParses = new AtomicInteger();
    final Queue<String> threads = new ConcurrentLinkedQueue<>();

    @Override
    public ParseResult parse(CharSequence text, long version) {
        fullParses.incrementAndGet();
        return result(text, version, new ParseMetrics(1, 0, true));
    }

    @Override
    public ParseResult reparse(ParseResult previous, CharSequence text, long version, Change change) {
        incrementalParses.incrementAndGet();
        return result(text, version, new ParseMetrics(1, 0, false));
    }

    private ParseResult result(CharSequence text, long version, ParseMetrics metrics) {
        threads.add(Thread.currentThread().getName());
        String source = text.toString();
        List<BlockSpan> spans = source.isEmpty()
                ? List.of(new BlockSpan(BlockKind.DOCUMENT, 0, 0, 0))
                : List.of(new BlockSpan(BlockKind.DOCUMENT, 0, source.length(), 0),
                new BlockSpan(BlockKind.PARAGRAPH, 0, source.length(), 1));
        return new Result(version, spans, metrics);
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
