package ru.mcs.core.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCreationTest {

    @TempDir
    Path temp;

    @Test
    void createsMissingProjectDirectoriesAndAcceptsExistingDirectory() throws Exception {
        Path root = temp.resolve("nested").resolve("manuscript");

        assertThat(ProjectController.createProjectDirectory(root)).isEqualTo(root);
        assertThat(Files.isDirectory(root)).isTrue();
        assertThat(ProjectController.createProjectDirectory(root)).isEqualTo(root);
    }

    @Test
    void rejectsRegularFileInsteadOfReplacingItWithProjectDirectory() throws Exception {
        Path root = Files.writeString(temp.resolve("occupied"), "keep me");

        assertThatThrownBy(() -> ProjectController.createProjectDirectory(root))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(root)).isEqualTo("keep me");
    }
}
