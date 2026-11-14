package ru.mcs.plugin.mdeditor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;
import ru.mcs.plugin.mdeditor.model.DocumentChange;
import ru.mcs.plugin.mdeditor.model.DocumentListener;
import ru.mcs.plugin.mdeditor.model.DocumentModel;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.editor.MarkdownParser.Change;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;

/** Последовательно парсит snapshots вне EDT и публикует только результат актуальной версии. */
final class MarkdownParseCoordinator implements DocumentListener, AutoCloseable {

    private final DocumentModel document;
    private final MarkdownParser parser;
    private final Executor executor;
    private final Object lock = new Object();

    private ParseRequest pending;
    private boolean running;
    private ParseResult lastResult;
    private volatile AstIndex index;
    private volatile boolean closed;

    MarkdownParseCoordinator(DocumentModel document, MarkdownParser parser, Executor executor) {
        this.document = Objects.requireNonNull(document, "document");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.executor = Objects.requireNonNull(executor, "executor");
        document.addListener(this);
        request(null);
    }

    AstIndex index() {
        return index;
    }

    @Override
    public void changed(DocumentChange change) {
        changed(List.of(change));
    }

    @Override
    public void changed(List<DocumentChange> changes) {
        Change change = changes.size() == 1 ? toChange(changes.getFirst()) : null;
        request(change);
    }

    private static Change toChange(DocumentChange change) {
        return new Change(change.start(), change.oldLen(), change.newLen());
    }

    private void request(Change change) {
        if (closed) {
            return;
        }
        ParseRequest request = new ParseRequest(document.text().toString(), document.version(), change);
        synchronized (lock) {
            if (closed) {
                return;
            }
            pending = request;
            if (running) {
                return;
            }
            running = true;
        }
        try {
            executor.execute(this::drain);
        } catch (RuntimeException e) {
            synchronized (lock) {
                running = false;
            }
            throw e;
        }
    }

    private void drain() {
        while (true) {
            ParseRequest request;
            synchronized (lock) {
                if (closed) {
                    pending = null;
                    running = false;
                    return;
                }
                request = pending;
                pending = null;
                if (request == null) {
                    running = false;
                    return;
                }
            }
            try {
                ParseResult result = parse(request);
                lastResult = result;
                SwingUtilities.invokeLater(() -> apply(result));
            } catch (RuntimeException e) {
                System.getLogger(MarkdownParseCoordinator.class.getName())
                        .log(System.Logger.Level.ERROR, "Markdown parsing failed", e);
            }
        }
    }

    private ParseResult parse(ParseRequest request) {
        if (request.change != null && lastResult != null
                && lastResult.version() + 1 == request.version) {
            return parser.reparse(lastResult, request.text, request.version, request.change);
        }
        return parser.parse(request.text, request.version);
    }

    private void apply(ParseResult result) {
        if (!closed && result.version() == document.version()) {
            index = new AstIndex(result);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        document.removeListener(this);
        synchronized (lock) {
            pending = null;
        }
    }

    private static final class ParseRequest {
        private final String text;
        private final long version;
        private final Change change;

        private ParseRequest(String text, long version, Change change) {
            this.text = text;
            this.version = version;
            this.change = change;
        }
    }
}
