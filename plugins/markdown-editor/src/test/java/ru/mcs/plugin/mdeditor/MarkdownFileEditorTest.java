package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.plugin.mdeditor.model.Insert;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.AtomicFileStore;

class MarkdownFileEditorTest {

    @TempDir
    Path temp;

    @Test
    void loadsOutsideEdtAndSwitchesModesInsideSameComponent() throws Exception {
        Path file = temp.resolve("note.md");
        String markdown = "---\ntitle: Test\n---\n# Heading\n\nBody";
        Files.writeString(file, markdown, StandardCharsets.UTF_8);
        MarkdownEditorRegistry registry = new MarkdownEditorRegistry();
        MarkdownFileEditor editor = create(file, EditorMode.WYSIWYG, registry);
        try {
            awaitLoaded(editor);
            JComponent original = editor.getComponent();

            assertThat(editor.isLoaded()).isTrue();
            assertThat(editor.mode()).isEqualTo(EditorMode.WYSIWYG);
            assertThat(editor.canvas().getStatus()).isEqualTo("canvas.ready");
            assertThat(sourceText(editor)).isEqualTo(markdown);
            assertThat(editor.isModified()).isFalse();

            SwingUtilities.invokeAndWait(() -> editor.setMode(EditorMode.SOURCE));
            assertThat(editor.mode()).isEqualTo(EditorMode.SOURCE);
            assertThat(editor.getComponent()).isSameAs(original);
            assertThat(sourceText(editor)).isEqualTo(markdown);

            SwingUtilities.invokeAndWait(() -> editor.setMode(EditorMode.WYSIWYG));
            assertThat(editor.mode()).isEqualTo(EditorMode.WYSIWYG);
            assertThat(editor.getComponent()).isSameAs(original);
            assertThat(registry.size()).isEqualTo(1);
        } finally {
            SwingUtilities.invokeAndWait(editor::dispose);
        }
        assertThat(registry.size()).isZero();
    }

    @Test
    void honorsSourceAsDefaultMode() throws Exception {
        Path file = temp.resolve("source.md");
        Files.writeString(file, "raw", StandardCharsets.UTF_8);
        MarkdownFileEditor editor = create(file, EditorMode.SOURCE, new MarkdownEditorRegistry());
        try {
            awaitLoaded(editor);
            assertThat(editor.mode()).isEqualTo(EditorMode.SOURCE);
            assertThat(sourceText(editor)).isEqualTo("raw");
            editor.save();
            assertThat(Files.readString(file)).isEqualTo("raw");
        } finally {
            SwingUtilities.invokeAndWait(editor::dispose);
        }
    }

    @Test
    void malformedUtf8BecomesVisibleErrorInsteadOfEditorCreationFailure() throws Exception {
        Path file = temp.resolve("broken.md");
        Files.write(file, new byte[]{(byte) 0xC3, 0x28});
        MarkdownFileEditor editor = create(file, EditorMode.WYSIWYG, new MarkdownEditorRegistry());
        try {
            awaitLoaded(editor);
            assertThat(sourceText(editor)).contains("source.error");
            assertThat(editor.canvas().getStatus()).contains("canvas.error");
        } finally {
            SwingUtilities.invokeAndWait(editor::dispose);
        }
    }

    @Test
    void modelEditsUpdateSourceModifiedListenersAndAtomicSavePreservesBomAndCrLf() throws Exception {
        Path file = temp.resolve("properties.md");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "one\r\ntwo\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] original = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, original, 0, bom.length);
        System.arraycopy(body, 0, original, bom.length, body.length);
        Files.write(file, original);
        MarkdownFileEditor editor = create(file, EditorMode.SOURCE, new MarkdownEditorRegistry());
        AtomicInteger modifiedNotifications = new AtomicInteger();
        try {
            awaitLoaded(editor);
            SwingUtilities.invokeAndWait(() -> {
                editor.addModifiedListener(modifiedNotifications::incrementAndGet);
                editor.document().apply(new Insert(editor.document().length(), "three"));
            });
            assertThat(sourceText(editor)).isEqualTo("one\ntwo\nthree");
            assertThat(editor.isModified()).isTrue();
            assertThat(modifiedNotifications).hasValue(1);

            SwingUtilities.invokeAndWait(() -> editor.document().undo().undo());
            assertThat(sourceText(editor)).isEqualTo("one\ntwo\n");
            assertThat(editor.isModified()).isFalse();
            SwingUtilities.invokeAndWait(() -> editor.document().undo().redo());
            assertThat(editor.isModified()).isTrue();

            SwingUtilities.invokeAndWait(() -> {
                try {
                    editor.save();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            assertThat(editor.isModified()).isFalse();
            assertThat(modifiedNotifications).hasValue(4);
            byte[] savedBody = "one\r\ntwo\r\nthree".getBytes(StandardCharsets.UTF_8);
            byte[] expected = new byte[bom.length + savedBody.length];
            System.arraycopy(bom, 0, expected, 0, bom.length);
            System.arraycopy(savedBody, 0, expected, bom.length, savedBody.length);
            assertThat(Files.readAllBytes(file)).isEqualTo(expected);

            SwingUtilities.invokeAndWait(() -> editor.document().undo().undo());
            assertThat(editor.isModified()).isTrue();
            SwingUtilities.invokeAndWait(() -> editor.document().undo().redo());
            assertThat(editor.isModified()).isFalse();
        } finally {
            SwingUtilities.invokeAndWait(editor::dispose);
        }
    }

    @Test
    void mixedEolSaveFailsWithoutTouchingOriginalFile() throws Exception {
        byte[] original = "a\r\nb\nc".getBytes(StandardCharsets.UTF_8);
        Path file = temp.resolve("mixed.md");
        Files.write(file, original);
        MarkdownFileEditor editor = create(file, EditorMode.SOURCE, new MarkdownEditorRegistry());
        try {
            awaitLoaded(editor);
            SwingUtilities.invokeAndWait(() -> editor.document().apply(new Insert(editor.document().length(), "d")));

            assertThatThrownBy(editor::save)
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessage("save.error.mixedEol");
            assertThat(Files.readAllBytes(file)).isEqualTo(original);
            assertThat(editor.isModified()).isTrue();
        } finally {
            SwingUtilities.invokeAndWait(editor::dispose);
        }
    }

    @Test
    void asyncSaveWritesOutsideEdtAndKeepsLaterEditsModified() throws Exception {
        Path file = Files.writeString(temp.resolve("async.md"), "one");
        TestAtomicFileStore delegate = new TestAtomicFileStore();
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch continueWrite = new CountDownLatch(1);
        AtomicReference<String> writeThread = new AtomicReference<>();
        AtomicFileStore blockingStore = new AtomicFileStore() {
            @Override
            public byte[] read(Path file) throws java.io.IOException {
                return delegate.read(file);
            }

            @Override
            public void write(Path file, byte[] content) throws java.io.IOException {
                writeThread.set(Thread.currentThread().getName());
                writeStarted.countDown();
                try {
                    if (!continueWrite.await(5, TimeUnit.SECONDS)) {
                        throw new java.io.IOException("Timed out waiting to continue test write");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException(e);
                }
                delegate.write(file, content);
            }
        };
        MarkdownFileEditor editor = create(file, EditorMode.SOURCE, new MarkdownEditorRegistry(), blockingStore);
        try {
            awaitLoaded(editor);
            SwingUtilities.invokeAndWait(() -> editor.document().apply(new Insert(editor.document().length(), " two")));
            AtomicReference<CompletableFuture<Void>> saving = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> saving.set(editor.saveAsync()));

            assertThat(writeStarted.await(5, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> {
                assertThat(editor.isModified()).isTrue();
                editor.document().apply(new Insert(editor.document().length(), " three"));
            });
            continueWrite.countDown();
            saving.get().get(5, TimeUnit.SECONDS);

            assertThat(writeThread.get()).doesNotContain("AWT-EventQueue");
            assertThat(Files.readString(file)).isEqualTo("one two");
            assertThat(editor.isModified()).isTrue();
            SwingUtilities.invokeAndWait(() -> editor.document().undo().undo());
            assertThat(editor.isModified()).isFalse();
            assertThat(sourceText(editor)).isEqualTo("one two");
            SwingUtilities.invokeAndWait(() -> editor.document().undo().redo());
            assertThat(editor.isModified()).isTrue();
        } finally {
            continueWrite.countDown();
            SwingUtilities.invokeAndWait(editor::dispose);
        }
    }

    @Test
    void disposeIsIdempotentAndRemovesRegistryEntry() throws Exception {
        Path file = temp.resolve("dispose.md");
        Files.writeString(file, "text");
        MarkdownEditorRegistry registry = new MarkdownEditorRegistry();
        MarkdownFileEditor editor = create(file, EditorMode.WYSIWYG, registry);

        SwingUtilities.invokeAndWait(() -> {
            editor.dispose();
            editor.dispose();
            editor.setMode(EditorMode.SOURCE);
        });

        assertThat(registry.size()).isZero();
        assertThat(editor.mode()).isEqualTo(EditorMode.WYSIWYG);
    }

    private MarkdownFileEditor create(Path file, EditorMode mode, MarkdownEditorRegistry registry) throws Exception {
        return create(file, mode, registry, new TestAtomicFileStore());
    }

    private MarkdownFileEditor create(Path file, EditorMode mode, MarkdownEditorRegistry registry,
                                      AtomicFileStore fileStore) throws Exception {
        AtomicReference<MarkdownFileEditor> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(new MarkdownFileEditor(file, mode, registry, I18n.passthrough(),
                fileStore)));
        return result.get();
    }

    private static String sourceText(MarkdownFileEditor editor) throws Exception {
        AtomicReference<String> text = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> text.set(editor.sourceText()));
        return text.get();
    }

    private static void awaitLoaded(MarkdownFileEditor editor) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!editor.isLoaded() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(editor.isLoaded()).as("editor background loading completes").isTrue();
    }
}
