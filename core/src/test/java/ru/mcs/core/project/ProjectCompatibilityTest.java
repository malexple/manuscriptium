package ru.mcs.core.project;

import ru.mcs.spi.project.ViewDefinition;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCompatibilityTest {

    @TempDir
    Path tmp;

    @Test
    void reopeningProjectPreservesExistingMetadataViewsSessionAndMarkdown() throws Exception {
        Path root = tmp.resolve("project");
        Path note = root.resolve("note.md");
        Path marker = ProjectPaths.metadataDir(root).resolve("keep.txt");
        Path session = ProjectPaths.stateDir(root).resolve("core.session.yml");
        Path view = ProjectPaths.viewsDir(root).resolve("tree.yml");
        Files.createDirectories(session.getParent());
        Files.createDirectories(view.getParent());
        Files.writeString(note, "# Existing note\n");
        Files.writeString(marker, "keep\n");
        Files.writeString(session, "tabs: []\n");
        Files.writeString(view, "name: Existing\nentries:\n  - file: note.md\n");

        for (int reopen = 0; reopen < 2; reopen++) {
            try (ProjectService service = ProjectService.open(root, snapshot -> { })) {
                assertThat(service.snapshot().notes()).extracting(ProjectNote::path).containsExactly("note.md");
                assertThat(service.snapshot().views()).extracting(ViewDefinition::name).containsExactly("Existing");
                assertThat(ProjectPaths.databaseFile(root)).exists();
                assertThat(ProjectPaths.luceneDir(root)).isDirectory();
            }
        }

        assertThat(Files.readString(note)).isEqualTo("# Existing note\n");
        assertThat(Files.readString(marker)).isEqualTo("keep\n");
        assertThat(Files.readString(session)).isEqualTo("tabs: []\n");
        assertThat(Files.readString(view)).isEqualTo("name: Existing\nentries:\n  - file: note.md\n");
    }
}
