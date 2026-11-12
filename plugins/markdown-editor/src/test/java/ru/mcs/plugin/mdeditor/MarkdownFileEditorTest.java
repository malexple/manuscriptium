package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.spi.i18n.I18n;

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
        AtomicReference<MarkdownFileEditor> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(new MarkdownFileEditor(file, mode, registry, I18n.passthrough())));
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
