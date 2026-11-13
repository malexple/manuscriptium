package ru.mcs.plugin.mdeditor.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DocumentModelImplTest {

    @Test
    void appliesCommandsAndPublishesPreciseChanges() {
        DocumentModelImpl model = model("abc");
        List<DocumentChange> changes = new ArrayList<>();
        model.addListener(changes::add);

        model.apply(new Insert(1, "X"));
        model.apply(Replace.of(0, 2, "YZ"));
        model.apply(Delete.of(1, 3));

        assertThat(model.text().toString()).isEqualTo("Yc");
        assertThat(model.version()).isEqualTo(3);
        assertThat(changes).containsExactly(
                new DocumentChange(1, 0, 1, 1),
                new DocumentChange(0, 2, 2, 2),
                new DocumentChange(1, 2, 0, 3));
    }

    @Test
    void nestedTransactionIsOneUndoStep() {
        DocumentModelImpl model = model("base");
        model.beginTransaction();
        model.apply(new Insert(4, "1"));
        model.beginTransaction();
        model.apply(new Insert(5, "2"));
        model.endTransaction();
        model.apply(Delete.of(0, 1));
        model.endTransaction();

        assertThat(model.text().toString()).isEqualTo("ase12");
        assertThat(model.undoCount()).isEqualTo(1);
        model.undo().undo();
        assertThat(model.text().toString()).isEqualTo("base");
        model.undo().redo();
        assertThat(model.text().toString()).isEqualTo("ase12");
    }

    @Test
    void invalidCompositeRollsBackItsEarlierParts() {
        DocumentModelImpl model = model("abc");

        assertThatThrownBy(() -> model.apply(Composite.of(new Insert(1, "X"), Delete.of(20, 21))))
                .isInstanceOf(IndexOutOfBoundsException.class);

        assertThat(model.text().toString()).isEqualTo("abc");
        assertThat(model.undo().canUndo()).isFalse();
        assertThat(model.isModified()).isFalse();
    }

    @Test
    void groupsOnlyAdjacentSameTypeEditsWithinPause() {
        AtomicLong time = new AtomicLong();
        DocumentModelImpl model = new DocumentModelImpl("", DocumentProperties.utf8(LineEnding.LF, false),
                500, time::get);

        model.apply(new Insert(0, "a"));
        time.addAndGet(100);
        model.apply(new Insert(1, "b"));
        assertThat(model.undoCount()).isEqualTo(1);

        time.addAndGet(501);
        model.apply(new Insert(2, "c"));
        assertThat(model.undoCount()).isEqualTo(2);

        model.apply(Delete.of(0, 1));
        assertThat(model.undoCount()).isEqualTo(3);
        model.undo().undo();
        assertThat(model.text().toString()).isEqualTo("abc");
    }

    @Test
    void groupsBackspaceAndForwardDeleteInOriginalOrder() {
        DocumentModelImpl backspace = new DocumentModelImpl("abcd", DocumentProperties.utf8(LineEnding.LF, false),
                500, () -> 0);
        backspace.apply(Delete.of(3, 4));
        backspace.apply(Delete.of(2, 3));
        assertThat(backspace.undoCount()).isEqualTo(1);
        backspace.undo().undo();
        assertThat(backspace.text().toString()).isEqualTo("abcd");

        DocumentModelImpl forward = new DocumentModelImpl("abcd", DocumentProperties.utf8(LineEnding.LF, false),
                500, () -> 0);
        forward.apply(Delete.of(1, 2));
        forward.apply(Delete.of(1, 2));
        assertThat(forward.undoCount()).isEqualTo(1);
        forward.undo().undo();
        assertThat(forward.text().toString()).isEqualTo("abcd");
    }

    @Test
    void savePointSurvivesGroupingUndoRedoAndNewBranches() {
        AtomicLong time = new AtomicLong();
        DocumentModelImpl model = new DocumentModelImpl("", DocumentProperties.utf8(LineEnding.LF, false),
                500, time::get);
        model.apply(new Insert(0, "a"));
        model.markSaved();
        assertThat(model.isModified()).isFalse();

        time.incrementAndGet();
        model.apply(new Insert(1, "b"));
        assertThat(model.undoCount()).as("save point must not be merged with the next edit").isEqualTo(2);
        assertThat(model.isModified()).isTrue();
        model.undo().undo();
        assertThat(model.text().toString()).isEqualTo("a");
        assertThat(model.isModified()).isFalse();
        model.undo().redo();
        assertThat(model.isModified()).isTrue();

        model.undo().undo();
        model.apply(new Insert(1, "c"));
        assertThat(model.text().toString()).isEqualTo("ac");
        assertThat(model.isModified()).isTrue();
        assertThat(model.undo().canRedo()).isFalse();
        model.undo().undo();
        assertThat(model.isModified()).isFalse();
    }

    @Test
    void clearingHistoryPreservesModifiedState() {
        DocumentModelImpl clean = model("abc");
        clean.undo().clear();
        assertThat(clean.isModified()).isFalse();

        clean.apply(new Insert(3, "d"));
        clean.undo().clear();
        assertThat(clean.isModified()).isTrue();
        assertThat(clean.undo().canUndo()).isFalse();
        assertThat(clean.undo().canRedo()).isFalse();

        clean.markSaved();
        assertThat(clean.isModified()).isFalse();
        clean.undo().clear();
        assertThat(clean.isModified()).isFalse();
    }

    @Test
    void convertsOffsetsAndPositionsInUtf16() {
        DocumentModelImpl model = model("a🙂\nbb\nlast");

        assertThat(model.position(0)).isEqualTo(new Position(0, 0));
        assertThat(model.position(4)).isEqualTo(new Position(1, 0));
        assertThat(model.position(6)).isEqualTo(new Position(1, 2));
        assertThat(model.position(7)).isEqualTo(new Position(2, 0));
        assertThat(model.offset(new Position(0, 3))).isEqualTo(3);
        assertThat(model.offset(new Position(1, 1))).isEqualTo(5);
        assertThat(model.offset(new Position(2, 4))).isEqualTo(model.length());
        assertThatThrownBy(() -> model.offset(new Position(1, 3))).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void randomizedCommandsUndoToOriginalAndRedoToFinalText() {
        Random random = new Random(0xE1D17);
        StringBuilder expected = new StringBuilder("original\n🙂 text");
        DocumentModelImpl model = new DocumentModelImpl(expected.toString(),
                DocumentProperties.utf8(LineEnding.LF, false), 100, () -> 0);
        String original = expected.toString();

        for (int operation = 0; operation < 600; operation++) {
            int kind = random.nextInt(3);
            if (expected.isEmpty()) {
                kind = 0;
            }
            if (kind == 0) {
                int offset = random.nextInt(expected.length() + 1);
                String value = operation % 7 == 0 ? "🙂" : operation % 5 == 0 ? "\n" : "x";
                expected.insert(offset, value);
                model.apply(new Insert(offset, value));
            } else {
                int start = random.nextInt(expected.length());
                int end = start + 1 + random.nextInt(expected.length() - start);
                if (kind == 1) {
                    String removed = expected.substring(start, end);
                    expected.delete(start, end);
                    model.apply(Delete.of(start, end));
                    assertThat(removed).isNotEmpty();
                } else {
                    expected.replace(start, end, "R");
                    model.apply(Replace.of(start, end, "R"));
                }
            }
        }
        String finalText = expected.toString();
        assertThat(model.text().toString()).isEqualTo(finalText);

        while (model.undo().canUndo()) {
            model.undo().undo();
        }
        assertThat(model.text().toString()).isEqualTo(original);
        assertThat(model.isModified()).isFalse();

        while (model.undo().canRedo()) {
            model.undo().redo();
        }
        assertThat(model.text().toString()).isEqualTo(finalText);
    }

    @Test
    void emptyCommandsAndUnbalancedTransactionsAreHandled() {
        DocumentModelImpl model = model("abc");
        model.apply(new Insert(0, ""));
        model.apply(Delete.of(1, 1));
        model.apply(Replace.of(1, 2, "b"));

        assertThat(model.version()).isZero();
        assertThat(model.undoCount()).isZero();
        assertThatThrownBy(model::endTransaction).isInstanceOf(IllegalStateException.class);
    }

    private static DocumentModelImpl model(String text) {
        return new DocumentModelImpl(text, DocumentProperties.utf8(LineEnding.LF, false));
    }
}
