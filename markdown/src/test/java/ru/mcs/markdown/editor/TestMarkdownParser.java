package ru.mcs.markdown.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;
import ru.mcs.markdown.api.MarkdownParser;

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
        if (source.isEmpty()) {
            return new Result(version, List.of(new BlockSpan(BlockKind.DOCUMENT, 0, 0, 0)), metrics);
        }
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, source.length(), 0));
        int closing = source.startsWith("---\n") ? source.indexOf("\n---\n", 4) : -1;
        if (closing >= 0) {
            int end = closing + 4;
            spans.add(new BlockSpan(BlockKind.FRONT_MATTER, 0, end, 1));
            if (end < source.length()) {
                spans.add(new BlockSpan(BlockKind.PARAGRAPH, end, source.length(), 1));
            }
        } else {
            spans.add(new BlockSpan(BlockKind.PARAGRAPH, 0, source.length(), 1));
        }
        return new Result(version, List.copyOf(spans), metrics);
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
