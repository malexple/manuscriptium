package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextFileEditorProviderTest {

    @TempDir
    Path tmp;

    private final TextFileEditorProvider provider = new TextFileEditorProvider();

    @Test
    void editAndSaveRoundTrip() throws IOException {
        Path file = Files.writeString(tmp.resolve("n.md"), "hello\nworld\n");
        TextFileEditor editor = (TextFileEditor) provider.createEditor(file);
        assertThat(editor.text()).isEqualTo("hello\nworld\n");
        assertThat(editor.isModified()).isFalse();

        editor.setText("changed\n");
        assertThat(editor.isModified()).isTrue();

        editor.save();
        assertThat(editor.isModified()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("changed\n");
    }

    @Test
    void crlfIsPreserved() throws IOException {
        Path file = Files.writeString(tmp.resolve("w.txt"), "a\r\nb\r\n");
        TextFileEditor editor = (TextFileEditor) provider.createEditor(file);
        assertThat(editor.text()).isEqualTo("a\nb\n");

        editor.setText("a\nb\nc\n");
        editor.save();
        assertThat(Files.readString(file)).isEqualTo("a\r\nb\r\nc\r\n");
    }

    @Test
    void modifiedListenerFiresOnlyOnChangeOfFlag() throws IOException {
        Path file = Files.writeString(tmp.resolve("x.txt"), "x");
        TextFileEditor editor = (TextFileEditor) provider.createEditor(file);
        int[] calls = {0};
        editor.addModifiedListener(() -> calls[0]++);
        editor.setText("1");
        editor.setText("12");
        assertThat(calls[0]).isEqualTo(1);
        editor.save();
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    void invalidUtf8IsRejected() throws IOException {
        Path file = tmp.resolve("bin.dat");
        Files.write(file, new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThatThrownBy(() -> provider.createEditor(file)).isInstanceOf(IOException.class);
    }

    @Test
    void utf8CyrillicSurvives() throws IOException {
        Path file = Files.writeString(tmp.resolve("ru.txt"), "Привет, мир", StandardCharsets.UTF_8);
        TextFileEditor editor = (TextFileEditor) provider.createEditor(file);
        assertThat(editor.text()).isEqualTo("Привет, мир");
    }

    @Test
    void acceptsOnlyRegularFilesWithinLimit() throws IOException {
        assertThat(provider.accept(tmp)).isFalse();
        assertThat(provider.accept(tmp.resolve("missing.txt"))).isFalse();

        Path small = Files.writeString(tmp.resolve("s.txt"), "s");
        assertThat(provider.accept(small)).isTrue();

        Path big = tmp.resolve("big.bin");
        try (RandomAccessFile f = new RandomAccessFile(big.toFile(), "rw")) {
            f.setLength(TextFileEditorProvider.MAX_BYTES + 1);
        }
        assertThat(provider.accept(big)).isFalse();
    }
}
