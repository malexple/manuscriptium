package ru.mcs.plugin.mdeditor.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TextBufferPerformanceTest {

    @Test
    void fiveMebibyteDocumentAcceptsCharacterEditsWithinBudget() {
        TextBuffer buffer = new TextBuffer("a".repeat(5 * 1024 * 1024));
        int operations = 200;
        int offset = 2 * 1024 * 1024;

        long started = System.nanoTime();
        for (int i = 0; i < operations; i++) {
            buffer.insert(offset++, "x");
        }
        long elapsed = System.nanoTime() - started;
        Duration average = Duration.ofNanos(elapsed / operations);

        assertThat(average).as("average Piece Table edit on a 5 MiB document")
                .isLessThan(Duration.ofMillis(10));
        assertThat(buffer.length()).isEqualTo(5 * 1024 * 1024 + operations);
        assertThat(buffer.getText(new Range(2 * 1024 * 1024, 2 * 1024 * 1024 + operations)))
                .isEqualTo("x".repeat(operations));
    }
}
