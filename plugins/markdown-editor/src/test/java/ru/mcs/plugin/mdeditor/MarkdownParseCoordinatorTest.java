package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.Insert;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.MarkdownParser;

class MarkdownParseCoordinatorTest {

    @Test
    void parsesOutsideEdtPublishesIndexAndUsesIncrementalForSingleEdit() throws Exception {
        TestMarkdownParser parser = new TestMarkdownParser();
        AtomicReference<DocumentModelImpl> document = new AtomicReference<>();
        AtomicReference<MarkdownParseCoordinator> coordinator = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            document.set(new DocumentModelImpl("alpha", DocumentProperties.utf8(LineEnding.LF, false)));
            coordinator.set(new MarkdownParseCoordinator(document.get(), parser, TestMarkdownParser.EXECUTOR));
        });
        try {
            AstIndex initial = awaitIndex(coordinator.get(), 0);
            assertThat(initial.topLevelBlocks()).singleElement()
                    .satisfies(span -> assertThat(span.kind()).isEqualTo(BlockKind.PARAGRAPH));
            assertThat(initial.blockAt(2)).contains(initial.topLevelBlocks().getFirst());
            assertThat(initial.spansAt(2)).extracting(span -> span.kind())
                    .containsExactly(BlockKind.DOCUMENT, BlockKind.PARAGRAPH);
            assertThat(initial.blockAt(5)).isEmpty();
            assertThat(parser.threads).allMatch(name -> !name.startsWith("AWT-EventQueue"));

            SwingUtilities.invokeAndWait(() -> document.get().apply(new Insert(2, "X")));
            AstIndex changed = awaitIndex(coordinator.get(), 1);
            assertThat(changed.spans().getFirst().end()).isEqualTo(6);
            assertThat(changed.metrics().fullParse()).isFalse();
            assertThat(parser.fullParses).hasValue(1);
            assertThat(parser.incrementalParses).hasValue(1);
        } finally {
            SwingUtilities.invokeAndWait(coordinator.get()::close);
        }
    }

    @Test
    void groupedUndoUsesFullParseBecauseIntermediateTextsAreUnavailable() throws Exception {
        TestMarkdownParser parser = new TestMarkdownParser();
        AtomicReference<DocumentModelImpl> document = new AtomicReference<>();
        AtomicReference<MarkdownParseCoordinator> coordinator = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            document.set(new DocumentModelImpl("ab", DocumentProperties.utf8(LineEnding.LF, false)));
            coordinator.set(new MarkdownParseCoordinator(document.get(), parser, TestMarkdownParser.EXECUTOR));
        });
        try {
            awaitIndex(coordinator.get(), 0);
            SwingUtilities.invokeAndWait(() -> {
                document.get().beginTransaction();
                document.get().apply(new Insert(1, "X"));
                document.get().apply(new Insert(2, "Y"));
                document.get().endTransaction();
            });

            AstIndex changed = awaitIndex(coordinator.get(), 2);
            assertThat(changed.metrics().fullParse()).isTrue();
            assertThat(parser.fullParses).hasValue(2);
            assertThat(parser.incrementalParses).hasValue(0);
        } finally {
            SwingUtilities.invokeAndWait(coordinator.get()::close);
        }
    }

    @Test
    void staleResultCannotOverwriteNewerDocumentVersion() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch continueFirst = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        TestMarkdownParser parser = new TestMarkdownParser() {
            @Override
            public ParseResult parse(CharSequence text, long version) {
                firstStarted.countDown();
                await(continueFirst);
                return super.parse(text, version);
            }

            @Override
            public ParseResult reparse(ParseResult previous, CharSequence text, long version, Change change) {
                ParseResult result = super.reparse(previous, text, version, change);
                secondFinished.countDown();
                return result;
            }
        };
        AtomicReference<DocumentModelImpl> document = new AtomicReference<>();
        AtomicReference<MarkdownParseCoordinator> coordinator = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            document.set(new DocumentModelImpl("old", DocumentProperties.utf8(LineEnding.LF, false)));
            coordinator.set(new MarkdownParseCoordinator(document.get(), parser, TestMarkdownParser.EXECUTOR));
        });
        try {
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> document.get().apply(new Insert(3, " newer")));
            continueFirst.countDown();

            AstIndex current = awaitIndex(coordinator.get(), 1);
            assertThat(current.spans().getFirst().end()).isEqualTo(document.get().length());
            assertThat(secondFinished.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            continueFirst.countDown();
            SwingUtilities.invokeAndWait(coordinator.get()::close);
        }
    }

    @Test
    void closePreventsQueuedResultFromBeingPublished() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch continueParse = new CountDownLatch(1);
        TestMarkdownParser parser = new TestMarkdownParser() {
            @Override
            public ParseResult parse(CharSequence text, long version) {
                started.countDown();
                await(continueParse);
                return super.parse(text, version);
            }
        };
        AtomicReference<MarkdownParseCoordinator> coordinator = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> coordinator.set(new MarkdownParseCoordinator(
                new DocumentModelImpl("text", DocumentProperties.utf8(LineEnding.LF, false)),
                parser, TestMarkdownParser.EXECUTOR)));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(coordinator.get()::close);
        continueParse.countDown();
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(50);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(coordinator.get().index()).isNull();
    }

    private static AstIndex awaitIndex(MarkdownParseCoordinator coordinator, long version) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            AstIndex index = coordinator.index();
            if (index != null && index.version() == version) {
                return index;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("AstIndex version " + version + " was not published");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for parser test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
