package ru.mcs.plugin.mdeditor.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** История с объединением соседних однотипных правок и атомарными транзакциями. */
public final class UndoManagerImpl implements UndoManager {

    static final long DEFAULT_MERGE_PAUSE_NANOS = 500_000_000L;

    private record HistoryEntry(List<EditCommand> forwardCommands,
                                List<EditCommand> inverseCommands,
                                DocumentModelImpl.ChangeKind kind,
                                long timestamp,
                                Object token) {

        EditCommand forward() {
            return new Composite(forwardCommands);
        }

        EditCommand inverse() {
            return new Composite(inverseCommands);
        }

        EditCommand lastForward() {
            return forwardCommands.get(forwardCommands.size() - 1);
        }
    }

    private final DocumentModelImpl model;
    private final long mergePauseNanos;
    private final LongSupplier clock;
    private final Deque<HistoryEntry> undo = new ArrayDeque<>();
    private final Deque<HistoryEntry> redo = new ArrayDeque<>();
    private final Object rootToken = new Object();
    private Object currentToken = rootToken;
    private Object mergeBarrierToken;

    UndoManagerImpl(DocumentModelImpl model, long mergePauseNanos, LongSupplier clock) {
        this.model = Objects.requireNonNull(model, "model");
        if (mergePauseNanos < 0) {
            throw new IllegalArgumentException("Merge pause must be non-negative");
        }
        this.mergePauseNanos = mergePauseNanos;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean canUndo() {
        return !undo.isEmpty();
    }

    @Override
    public boolean canRedo() {
        return !redo.isEmpty();
    }

    @Override
    public void undo() {
        HistoryEntry entry = undo.peek();
        if (entry == null) {
            return;
        }
        model.replay(entry.inverse());
        undo.pop();
        redo.push(entry);
        currentToken = undo.isEmpty() ? rootToken : undo.peek().token();
        model.flushChanges();
    }

    @Override
    public void redo() {
        HistoryEntry entry = redo.peek();
        if (entry == null) {
            return;
        }
        model.replay(entry.forward());
        redo.pop();
        undo.push(entry);
        currentToken = entry.token();
        model.flushChanges();
    }

    @Override
    public void clear() {
        boolean wasModified = model.isModified();
        undo.clear();
        redo.clear();
        currentToken = new Object();
        mergeBarrierToken = null;
        model.historyCleared(wasModified);
    }

    void record(DocumentModelImpl.AppliedEdit applied) {
        redo.clear();
        long now = clock.getAsLong();
        HistoryEntry previous = undo.peek();
        if (previous != null && canMerge(previous, applied, now)) {
            undo.pop();
            List<EditCommand> forward = new ArrayList<>(previous.forwardCommands());
            forward.add(applied.forward());
            List<EditCommand> inverse = new ArrayList<>();
            inverse.add(applied.inverse());
            inverse.addAll(previous.inverseCommands());
            undo.push(new HistoryEntry(List.copyOf(forward), List.copyOf(inverse), applied.kind(), now, new Object()));
        } else {
            undo.push(new HistoryEntry(List.of(applied.forward()), List.of(applied.inverse()),
                    applied.kind(), now, new Object()));
        }
        currentToken = undo.peek().token();
    }

    Object currentToken() {
        return currentToken;
    }

    void protectSavePoint(Object token) {
        mergeBarrierToken = token;
    }

    int undoCount() {
        return undo.size();
    }

    int redoCount() {
        return redo.size();
    }

    private boolean canMerge(HistoryEntry previous, DocumentModelImpl.AppliedEdit next, long now) {
        if (previous.kind() != next.kind() || next.kind() == DocumentModelImpl.ChangeKind.OTHER
                || previous.token() == mergeBarrierToken || model.isSavedToken(previous.token())) {
            return false;
        }
        long elapsed = now - previous.timestamp();
        return elapsed >= 0 && elapsed <= mergePauseNanos && adjacent(previous.lastForward(), next.forward());
    }

    private static boolean adjacent(EditCommand previous, EditCommand next) {
        if (previous instanceof Insert left && next instanceof Insert right) {
            return right.offset() == left.offset() + left.text().length();
        }
        if (previous instanceof Delete left && next instanceof Delete right) {
            return right.range().start() == left.range().start()
                    || right.range().end() == left.range().start();
        }
        return false;
    }
}
