package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorActionsTest {

    @TempDir
    Path temp;

    @Test
    void createsEmptyFileWithoutOverwritingExistingContent() throws Exception {
        Path file = temp.resolve("standalone.md");

        assertThat(EditorActions.createNewFile(file)).isEqualTo(file);
        assertThat(Files.isRegularFile(file)).isTrue();
        assertThat(Files.size(file)).isZero();

        Files.writeString(file, "keep me");
        assertThatThrownBy(() -> EditorActions.createNewFile(file))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(file)).isEqualTo("keep me");
    }
}
