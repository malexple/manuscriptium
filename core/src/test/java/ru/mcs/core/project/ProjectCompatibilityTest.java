package ru.mcs.core.project;

import java.io.IOException;
import java.util.List;
import ru.mcs.spi.project.FullTextIndex;
import ru.mcs.spi.project.IndexedDocument;
import ru.mcs.spi.project.SearchHit;
import ru.mcs.spi.project.SearchQuery;
import ru.mcs.spi.project.ViewDefinition;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCompatibilityTest {

    @TempDir
    Path tmp;

    /** Ядро выдаёт фабрике каталог индекса; сам поиск живёт в модуле search, здесь нужен только каталог. */
    private static final class DirectoryOnlyFullTextIndex implements FullTextIndex {

        DirectoryOnlyFullTextIndex(Path directory) throws IOException {
            Files.createDirectories(directory);
        }

        @Override
        public void replaceAll(List<IndexedDocument> documents) {
        }

        @Override
        public void update(IndexedDocument document) {
        }

        @Override
        public void removePath(String relative) {
        }

        @Override
        public List<SearchHit> search(Path root, SearchQuery query) {
            return List.of();
        }

        @Override
        public void close() {
        }
    }

    @Test
    void projectWorksWithoutFullTextSearchModule() throws Exception {
        Path root = tmp.resolve("plain");
        Files.createDirectories(root);
        Files.writeString(root.resolve("note.md"), "# Note");
        try (ProjectService service = ProjectService.open(root, snapshot -> { })) {
            assertThat(service.snapshot().notes()).extracting(ProjectNote::path).containsExactly("note.md");
            assertThat(ProjectPaths.databaseFile(root)).exists();
            assertThat(ProjectPaths.luceneDir(root)).doesNotExist();
            assertThat(service.search(new SearchQuery("note", null, "", java.util.Map.of(),
                    SearchQuery.DEFAULT_LIMIT)).get()).isEmpty();
        }
    }

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
            try (ProjectService service = ProjectService.open(root, snapshot -> { }, new ProjectIndexContributors(),
                    DirectoryOnlyFullTextIndex::new)) {
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
