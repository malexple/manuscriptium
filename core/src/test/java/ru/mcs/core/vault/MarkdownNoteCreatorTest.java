package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MarkdownNoteCreatorTest {

    @TempDir
    Path tmp;

    @Test
    void createsNestedUtf8NoteWithoutFollowingLinks() throws Exception {
        Path created = MarkdownNoteCreator.create(tmp, "Идеи/Новая заметка.md");

        assertThat(created).isEqualTo(tmp.resolve("Идеи/Новая заметка.md"));
        assertThat(Files.readString(created)).isEqualTo("# Новая заметка\n\n");
        assertThat(Files.isRegularFile(created, LinkOption.NOFOLLOW_LINKS)).isTrue();
        assertThat(Files.isSymbolicLink(created.getParent())).isFalse();
    }

    @Test
    void neverOverwritesAnExistingFile() throws Exception {
        Path existing = Files.writeString(tmp.resolve("note.md"), "keep me");

        assertThatThrownBy(() -> MarkdownNoteCreator.create(tmp, "note.md"))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(existing)).isEqualTo("keep me");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../escape.md", "/absolute.md", "C:/absolute.md", "folder\\escape.md",
            ".manuscriptium/escape.md", ".git/escape.md", "note.txt", "folder//note.md", "./note.md"
    })
    void rejectsInvalidOrEscapingPaths(String path) {
        assertThatThrownBy(() -> MarkdownNoteCreator.create(tmp, path))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankPaths(String path) {
        assertThatThrownBy(() -> MarkdownNoteCreator.create(tmp, path))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRegularFileAsParent() throws Exception {
        Files.writeString(tmp.resolve("blocker"), "not a directory");

        assertThatThrownBy(() -> MarkdownNoteCreator.create(tmp, "blocker/note.md"))
                .isInstanceOf(IOException.class);
        assertThat(Files.exists(tmp.resolve("blocker/note.md"), LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    @Test
    void rejectsSymlinkAsParentAndDoesNotWriteOutsideVault() throws Exception {
        Path vault = Files.createDirectory(tmp.resolve("vault"));
        Path outside = Files.createDirectory(tmp.resolve("outside"));
        assumeSymlink(vault.resolve("linked"), outside);

        assertThatThrownBy(() -> MarkdownNoteCreator.create(vault, "linked/escape.md"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("real directory");
        assertThat(outside.resolve("escape.md")).doesNotExist();
    }

    @Test
    void rejectsExistingSymlinkAsTarget() throws Exception {
        Path outside = Files.writeString(tmp.resolve("outside.md"), "keep me");
        assumeSymlink(tmp.resolve("note.md"), outside);

        assertThatThrownBy(() -> MarkdownNoteCreator.create(tmp, "note.md"))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(outside)).isEqualTo("keep me");
    }

    private static void assumeSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.assumeTrue(false, "Symbolic links are not available: " + e.getMessage());
        }
    }
}
