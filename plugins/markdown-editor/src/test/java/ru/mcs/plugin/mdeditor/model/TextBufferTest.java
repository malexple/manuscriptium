package ru.mcs.plugin.mdeditor.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;

class TextBufferTest {

    @Test
    void insertsDeletesAndReadsAcrossPieces() {
        TextBuffer buffer = new TextBuffer("hello world");
        buffer.insert(5, ", brave");
        buffer.insert(0, ">> ");
        buffer.delete(new Range(3, 10));

        assertThat(buffer.toString()).isEqualTo(">> brave world");
        assertThat(buffer.getText(new Range(3, 8))).isEqualTo("brave");
        assertThat(buffer.charAt(3)).isEqualTo('b');
        assertThat(buffer.subSequence(9, 14)).isEqualTo("world");
        assertThat(buffer.length()).isEqualTo(">> brave world".length());
    }

    @Test
    void coalescesSequentialAppends() {
        TextBuffer buffer = new TextBuffer("a");
        buffer.insert(1, "b");
        buffer.insert(2, "c");
        buffer.insert(3, "d");

        assertThat(buffer.toString()).isEqualTo("abcd");
        assertThat(buffer.pieceCount()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidRangesWithoutChangingText() {
        TextBuffer buffer = new TextBuffer("abc");

        assertThatThrownBy(() -> buffer.insert(4, "x")).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.delete(new Range(1, 4))).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> buffer.charAt(3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(buffer.toString()).isEqualTo("abc");
    }

    @Test
    void randomizedEditsMatchStringBuilder() {
        Random random = new Random(0x5EED_E1);
        String[] additions = {"a", "bc", "\n", "🙂", " ", "XYZ"};
        StringBuilder expected = new StringBuilder("initial\ntext");
        TextBuffer actual = new TextBuffer(expected.toString());

        for (int operation = 0; operation < 1_500; operation++) {
            if (expected.isEmpty() || random.nextBoolean()) {
                int offset = random.nextInt(expected.length() + 1);
                String addition = additions[random.nextInt(additions.length)];
                expected.insert(offset, addition);
                actual.insert(offset, addition);
            } else {
                int start = random.nextInt(expected.length());
                int end = start + 1 + random.nextInt(expected.length() - start);
                expected.delete(start, end);
                actual.delete(new Range(start, end));
            }
            if (operation % 25 == 0) {
                assertThat(actual.toString()).as("operation %s", operation).isEqualTo(expected.toString());
            }
        }
        assertThat(actual.toString()).isEqualTo(expected.toString());
        assertThat(actual.length()).isEqualTo(expected.length());
    }
}
