package ru.mcs.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreImplTest {

    private final AtomicFileStoreImpl store = new AtomicFileStoreImpl();

    @TempDir
    Path temp;

    @Test
    void readsAndAtomicallyReplacesExistingFile() throws Exception {
        Path file = temp.resolve("note.md");
        Files.writeString(file, "old");

        assertThat(store.read(file)).isEqualTo("old".getBytes(StandardCharsets.UTF_8));
        store.write(file, "new".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(file)).isEqualTo("new");
        assertThat(Files.list(temp)).noneMatch(path -> path.getFileName().toString().endsWith(".mcs-tmp"));
    }

    @Test
    void preservesSymbolicLinkAndReplacesItsTarget() throws Exception {
        Path target = temp.resolve("target.md");
        Path link = temp.resolve("link.md");
        Files.writeString(target, "old");
        createSymbolicLink(link, target);

        store.write(link, "new".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("new");
        assertThat(Files.readString(link)).isEqualTo("new");
        assertThat(store.read(link)).isEqualTo("new".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void resolvesSymbolicLinkInParentWithoutReplacingIt() throws Exception {
        Path realDirectory = temp.resolve("real");
        Path linkedDirectory = temp.resolve("linked");
        Files.createDirectory(realDirectory);
        Path file = realDirectory.resolve("note.md");
        Files.writeString(file, "old");
        createSymbolicLink(linkedDirectory, realDirectory);

        store.write(linkedDirectory.resolve("note.md"), "new".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.isSymbolicLink(linkedDirectory)).isTrue();
        assertThat(Files.readString(file)).isEqualTo("new");
    }

    @Test
    void rejectsMissingAndNonRegularFiles() throws Exception {
        Path missing = temp.resolve("missing.md");
        assertThatThrownBy(() -> store.write(missing, new byte[0])).isInstanceOf(NoSuchFileException.class);
        assertThatThrownBy(() -> store.read(temp)).isInstanceOf(IOException.class)
                .hasMessageContaining("Not a regular file");
    }

    private static void createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException | IOException e) {
            Assumptions.abort("Symbolic links are unavailable in this test environment: " + e);
        }
    }
}
