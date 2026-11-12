package ru.mcs.plugin.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownFileStoreTest {

    @TempDir
    Path temp;

    @Test
    void readsAndAtomicallyReplacesRegularFile() throws IOException {
        Path note = Files.writeString(temp.resolve("note.md"), "old");

        assertThat(MarkdownFileStore.read(note)).isEqualTo("old");
        MarkdownFileStore.writeAtomically(note, "new\r\n");

        assertThat(Files.readString(note)).isEqualTo("new\r\n");
        try (var files = Files.list(temp)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("note.md");
        }
    }

    @Test
    void rejectsSymlinkTargetWithoutChangingItsDestination() throws IOException {
        Path destination = Files.writeString(temp.resolve("destination.md"), "safe");
        Path link = temp.resolve("link.md");
        try {
            Files.createSymbolicLink(link, destination);
        } catch (UnsupportedOperationException | IOException e) {
            return; // Файловая система без symlink: основной regular-file сценарий всё равно проверен.
        }

        assertThatIOException().isThrownBy(() -> MarkdownFileStore.read(link));
        assertThatIOException().isThrownBy(() -> MarkdownFileStore.writeAtomically(link, "bad"));
        assertThat(Files.readString(destination)).isEqualTo("safe");
    }

    @Test
    void rejectsSymlinkInParentDirectory() throws IOException {
        Path realDirectory = Files.createDirectory(temp.resolve("real"));
        Path note = Files.writeString(realDirectory.resolve("note.md"), "safe");
        Path link = temp.resolve("linked");
        try {
            Files.createSymbolicLink(link, realDirectory);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }

        assertThatIOException().isThrownBy(() -> MarkdownFileStore.read(link.resolve("note.md")));
        assertThat(Files.readString(note)).isEqualTo("safe");
    }

    @Test
    void rejectsMissingFile() {
        assertThatIOException().isThrownBy(() -> MarkdownFileStore.read(temp.resolve("missing.md")));
    }
}
