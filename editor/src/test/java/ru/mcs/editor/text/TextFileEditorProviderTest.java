package ru.mcs.editor.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class TextFileEditorProviderTest {

    @TempDir
    Path tmp;

    private final TextFileEditorProvider provider = new TextFileEditorProvider();

    @Test
    void loadsOutsideEdtEditsAndSavesThroughCanvasModel() throws Exception {
        Path file = Files.writeString(tmp.resolve("note.txt"), "hello\nworld\n");
        TextFileEditor editor = open(file);

        assertThat(editor.text()).isEqualTo("hello\nworld\n");
        assertThat(editor.canvas().document()).isSameAs(editor.document());
        assertThat(editor.isModified()).isFalse();

        int[] calls = {0};
        editor.addModifiedListener(() -> calls[0]++);
        SwingUtilities.invokeAndWait(() -> editor.setText("changed\n"));
        assertThat(editor.isModified()).isTrue();
        editor.save();
        assertThat(editor.isModified()).isFalse();
        assertThat(calls[0]).isEqualTo(2);
        assertThat(Files.readString(file)).isEqualTo("changed\n");
    }

    @Test
    void preservesUtf8BomAndCrLf() throws Exception {
        Path file = tmp.resolve("windows.txt");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "Привет\r\nмир\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] original = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, original, 0, bom.length);
        System.arraycopy(body, 0, original, bom.length, body.length);
        Files.write(file, original);

        TextFileEditor editor = open(file);
        assertThat(editor.text()).isEqualTo("Привет\nмир\n");
        SwingUtilities.invokeAndWait(() -> editor.setText("Привет\nмир\n!\n"));
        editor.save();

        byte[] saved = Files.readAllBytes(file);
        assertThat(saved).startsWith(bom);
        assertThat(new String(saved, 3, saved.length - 3, StandardCharsets.UTF_8))
                .isEqualTo("Привет\r\nмир\r\n!\r\n");
    }

    @Test
    void preservesCrAndUndoRestoresSavePoint() throws Exception {
        Path file = Files.writeString(tmp.resolve("classic.txt"), "a\rb\r");
        TextFileEditor editor = open(file);
        assertThat(editor.text()).isEqualTo("a\nb\n");
        SwingUtilities.invokeAndWait(() -> editor.setText("changed\n"));
        assertThat(editor.document().undo().canUndo()).isTrue();
        SwingUtilities.invokeAndWait(() -> editor.document().undo().undo());
        assertThat(editor.text()).isEqualTo("a\nb\n");
        assertThat(editor.isModified()).isFalse();
        SwingUtilities.invokeAndWait(() -> editor.document().undo().redo());
        assertThat(editor.isModified()).isTrue();
        editor.save();
        assertThat(Files.readString(file)).isEqualTo("changed\r");
    }

    @Test
    void rejectsInvalidUtf8WithoutCreatingDocument() throws Exception {
        Path file = tmp.resolve("broken.dat");
        Files.write(file, new byte[]{(byte) 0xC3, (byte) 0x28});
        TextFileEditor editor = open(file);
        assertThat(editor.document()).isNull();
        assertThat(editor.canvas().getStatus()).isNotBlank();
    }

    @Test
    void mixedLineEndingsRequireExplicitNormalizationBeforeSave() throws Exception {
        Path file = Files.write(tmp.resolve("mixed.txt"), "a\r\nb\n".getBytes(StandardCharsets.UTF_8));
        TextFileEditor editor = open(file);
        SwingUtilities.invokeAndWait(() -> editor.setText("changed\n"));
        assertThatThrownBy(editor::save).isInstanceOf(IOException.class)
                .hasMessageContaining("Mixed line endings");
        assertThat(Files.readString(file)).isEqualTo("a\r\nb\n");
    }

    @Test
    void acceptsOnlyRegularFilesWithinLimit() throws IOException {
        assertThat(provider.accept(tmp)).isFalse();
        assertThat(provider.accept(tmp.resolve("missing.txt"))).isFalse();
        Path small = Files.writeString(tmp.resolve("small.txt"), "s");
        assertThat(provider.accept(small)).isTrue();
        Path big = tmp.resolve("big.bin");
        try (RandomAccessFile stream = new RandomAccessFile(big.toFile(), "rw")) {
            stream.setLength(TextFileEditorProvider.MAX_BYTES + 1);
        }
        assertThat(provider.accept(big)).isFalse();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void atomicSaveFollowsSymlinkAndKeepsLinkItself() throws Exception {
        Path target = Files.writeString(tmp.resolve("target.txt"), "old");
        Path link = Files.createSymbolicLink(tmp.resolve("link.txt"), target.getFileName());
        TextFileEditor editor = open(link);
        SwingUtilities.invokeAndWait(() -> editor.setText("new\n"));
        editor.save();

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("new\n");
        try (var files = Files.list(tmp)) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".mcs-tmp"))).isEmpty();
        }
    }

    private TextFileEditor open(Path file) throws Exception {
        AtomicReference<TextFileEditor> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set((TextFileEditor) provider.createEditor(file));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        awaitLoaded(result.get());
        return result.get();
    }

    private static void awaitLoaded(TextFileEditor editor) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!editor.isLoaded() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(editor.isLoaded()).isTrue();
    }
}
