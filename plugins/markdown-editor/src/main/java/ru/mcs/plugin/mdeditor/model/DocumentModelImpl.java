package ru.mcs.plugin.mdeditor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/** Реализация DocumentModel поверх Piece Table. */
public final class DocumentModelImpl implements DocumentModel {

    enum ChangeKind {
        INSERT,
        DELETE,
        OTHER
    }

    record AppliedEdit(EditCommand forward, EditCommand inverse, ChangeKind kind) {
    }

    private final TextBuffer buffer;
    private final DocumentProperties properties;
    private final List<DocumentListener> listeners = new CopyOnWriteArrayList<>();
    private final List<DocumentChange> pendingChanges = new ArrayList<>();
    private final UndoManagerImpl undo;
    private long version;
    private int transactionDepth;
    private List<AppliedEdit> transaction;
    private Object savedToken;

    public DocumentModelImpl(String text, DocumentProperties properties) {
        this(text, properties, UndoManagerImpl.DEFAULT_MERGE_PAUSE_NANOS, System::nanoTime);
    }

    DocumentModelImpl(String text, DocumentProperties properties, long mergePauseNanos, LongSupplier clock) {
        buffer = new TextBuffer(text);
        this.properties = Objects.requireNonNull(properties, "properties");
        undo = new UndoManagerImpl(this, mergePauseNanos, clock);
        savedToken = undo.currentToken();
    }

    @Override
    public CharSequence text() {
        return buffer;
    }

    @Override
    public int length() {
        return buffer.length();
    }

    @Override
    public long version() {
        return version;
    }

    @Override
    public void apply(EditCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        try {
            AppliedEdit applied = applyInternal(cmd);
            if (applied == null) {
                return;
            }
            if (transactionDepth > 0) {
                transaction.add(applied);
            } else {
                undo.record(applied);
            }
        } finally {
            if (transactionDepth == 0) {
                flushChanges();
            }
        }
    }

    @Override
    public void beginTransaction() {
        if (transactionDepth == 0) {
            transaction = new ArrayList<>();
        }
        transactionDepth++;
    }

    @Override
    public void endTransaction() {
        if (transactionDepth == 0) {
            throw new IllegalStateException("No document transaction is active");
        }
        transactionDepth--;
        if (transactionDepth > 0) {
            return;
        }
        List<AppliedEdit> completed = transaction;
        transaction = null;
        AppliedEdit combined = combine(completed, ChangeKind.OTHER);
        if (combined != null) {
            undo.record(combined);
        }
        flushChanges();
    }

    @Override
    public void addListener(DocumentListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(DocumentListener listener) {
        listeners.remove(listener);
    }

    @Override
    public UndoManager undo() {
        return undo;
    }

    @Override
    public DocumentProperties properties() {
        return properties;
    }

    @Override
    public Position position(int offset) {
        TextBuffer.checkPosition(offset, length());
        int line = 0;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (buffer.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new Position(line, offset - lineStart);
    }

    @Override
    public int offset(Position position) {
        Objects.requireNonNull(position, "position");
        int line = 0;
        int lineStart = 0;
        while (line < position.line()) {
            if (lineStart >= length()) {
                throw new IndexOutOfBoundsException("Position outside document: " + position);
            }
            char ch = buffer.charAt(lineStart);
            lineStart++;
            if (ch == '\n') {
                line++;
            }
        }
        int lineEnd = lineStart;
        while (lineEnd < length() && buffer.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        int offset = lineStart + position.column();
        if (offset > lineEnd) {
            throw new IndexOutOfBoundsException("Position outside document: " + position);
        }
        return offset;
    }

    @Override
    public boolean isModified() {
        return savedToken != undo.currentToken();
    }

    @Override
    public SavePoint savePoint() {
        Object token = undo.currentToken();
        undo.protectSavePoint(token);
        return new SavePoint(token);
    }

    @Override
    public void markSaved(SavePoint savePoint) {
        savedToken = Objects.requireNonNull(savePoint, "savePoint").token();
    }

    void historyCleared(boolean wasModified) {
        // Очистка истории не является редактированием и не должна менять признак modified.
        savedToken = wasModified ? new Object() : undo.currentToken();
    }

    void replay(EditCommand command) {
        if (transactionDepth > 0) {
            throw new IllegalStateException("Undo/redo is not allowed inside a transaction");
        }
        applyInternal(command);
    }

    boolean isSavedToken(Object token) {
        return savedToken == token;
    }

    int undoCount() {
        return undo.undoCount();
    }

    int redoCount() {
        return undo.redoCount();
    }

    private AppliedEdit applyInternal(EditCommand command) {
        if (command instanceof Composite composite) {
            List<AppliedEdit> parts = new ArrayList<>();
            try {
                for (EditCommand part : composite.commands()) {
                    AppliedEdit applied = applyInternal(part);
                    if (applied != null) {
                        parts.add(applied);
                    }
                }
            } catch (RuntimeException | Error error) {
                for (int i = parts.size() - 1; i >= 0; i--) {
                    applyInternal(parts.get(i).inverse());
                }
                throw error;
            }
            return combine(parts, ChangeKind.OTHER);
        }
        if (command instanceof Insert insert) {
            return insert(insert, ChangeKind.INSERT);
        }
        if (command instanceof Delete delete) {
            return delete(delete, ChangeKind.DELETE);
        }
        if (command instanceof Replace replace) {
            return replace(replace);
        }
        throw new IllegalArgumentException("Unsupported edit command: " + command);
    }

    private AppliedEdit insert(Insert insert, ChangeKind kind) {
        TextBuffer.checkPosition(insert.offset(), length());
        if (insert.text().isEmpty()) {
            return null;
        }
        buffer.insert(insert.offset(), insert.text());
        pendingChanges.add(new DocumentChange(insert.offset(), 0, insert.text().length(), ++version));
        Delete inverse = Delete.of(insert.offset(), insert.offset() + insert.text().length());
        return new AppliedEdit(insert, inverse, kind);
    }

    private AppliedEdit delete(Delete delete, ChangeKind kind) {
        TextBuffer.checkRange(delete.range(), length());
        if (delete.range().length() == 0) {
            return null;
        }
        String oldText = buffer.getText(delete.range());
        buffer.delete(delete.range());
        pendingChanges.add(new DocumentChange(delete.range().start(), oldText.length(), 0, ++version));
        Insert inverse = new Insert(delete.range().start(), oldText);
        return new AppliedEdit(delete, inverse, kind);
    }

    private AppliedEdit replace(Replace replace) {
        TextBuffer.checkRange(replace.range(), length());
        String oldText = buffer.getText(replace.range());
        if (oldText.equals(replace.text())) {
            return null;
        }
        buffer.delete(replace.range());
        buffer.insert(replace.range().start(), replace.text());
        pendingChanges.add(new DocumentChange(replace.range().start(), oldText.length(), replace.text().length(), ++version));
        Replace inverse = Replace.of(replace.range().start(), replace.range().start() + replace.text().length(), oldText);
        return new AppliedEdit(replace, inverse, ChangeKind.OTHER);
    }

    private static AppliedEdit combine(List<AppliedEdit> parts, ChangeKind kind) {
        if (parts.isEmpty()) {
            return null;
        }
        List<EditCommand> forward = new ArrayList<>(parts.size());
        List<EditCommand> inverse = new ArrayList<>(parts.size());
        for (AppliedEdit part : parts) {
            forward.add(part.forward());
        }
        for (int i = parts.size() - 1; i >= 0; i--) {
            inverse.add(parts.get(i).inverse());
        }
        return new AppliedEdit(new Composite(forward), new Composite(inverse), kind);
    }

    void flushChanges() {
        if (pendingChanges.isEmpty()) {
            return;
        }
        List<DocumentChange> changes = List.copyOf(pendingChanges);
        pendingChanges.clear();
        for (DocumentChange change : changes) {
            for (DocumentListener listener : listeners) {
                listener.changed(change);
            }
        }
    }
}
