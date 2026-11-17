package ru.mcs.core.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceneProjectIndexTest {

    @TempDir
    Path tmp;

    @Test
    void searchesUnicodeContentMetadataPathAndTags() throws Exception {
        Files.createDirectories(tmp.resolve("notes"));
        Files.writeString(tmp.resolve("notes/java.md"), """
                ---
                title: "Java 21"
                tags: [java, backend]
                draft: false
                author: Alice
                ---
                Полнотекстовый Поиск и C++ без специального синтаксиса.
                """);
        Files.writeString(tmp.resolve("notes/draft.md"), """
                ---
                title: Черновик
                tags:
                  - notes
                draft: true
                ---
                secretneedle
                """);
        Files.writeString(tmp.resolve("root.md"), "# Root\njava outside the notes subtree");

        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            assertThat(ProjectPaths.luceneDir(tmp)).isDirectory();

            assertThat(paths(index, query("пОиСк", ProjectSearchScope.ALL)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("java", ProjectSearchScope.TITLE)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("Alice", ProjectSearchScope.METADATA)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("Alice", ProjectSearchScope.CONTENT))).isEmpty();
            assertThat(paths(index, query("backend", ProjectSearchScope.ATTRIBUTES)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("java.md", ProjectSearchScope.PATH)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("Поиск отсутствующий", ProjectSearchScope.ALL))).isEmpty();
            assertThat(paths(index, query("[::*", ProjectSearchScope.ALL))).isEmpty();
            assertThat(index.search(query("пОиСк", ProjectSearchScope.ALL)).getFirst().snippet())
                    .contains("Поиск");

            ProjectSearchQuery notesOnly = new ProjectSearchQuery("java", ProjectSearchScope.ALL,
                    "notes/", Map.of(), 100);
            assertThat(index.search(notesOnly)).extracting(ProjectSearchResult::path)
                    .containsExactly("notes/java.md");
            ProjectSearchQuery drafts = new ProjectSearchQuery("", ProjectSearchScope.ALL,
                    "notes", Map.of("draft", "true"), 100);
            assertThat(index.search(drafts)).extracting(ProjectSearchResult::path)
                    .containsExactly("notes/draft.md");
            ProjectSearchQuery published = new ProjectSearchQuery("", ProjectSearchScope.ALL,
                    "notes", Map.of("draft", "false"), 100);
            assertThat(index.search(published)).extracting(ProjectSearchResult::path)
                    .containsExactly("notes/java.md");
        }
    }

    @Test
    void incrementallyUpdatesAndDeletesLuceneDocuments() throws Exception {
        Files.createDirectories(tmp.resolve("tree"));
        Path note = tmp.resolve("tree/note.md");
        Files.writeString(note, "# Before\noldneedle");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            assertThat(paths(index, query("oldneedle", ProjectSearchScope.CONTENT)))
                    .containsExactly("tree/note.md");

            Files.writeString(note, """
                    ---
                    title: After
                    tags: [newtag]
                    draft: true
                    ---
                    newneedle
                    """);
            index.refresh(note);
            assertThat(paths(index, query("oldneedle", ProjectSearchScope.ALL))).isEmpty();
            assertThat(paths(index, query("newneedle", ProjectSearchScope.CONTENT)))
                    .containsExactly("tree/note.md");
            assertThat(paths(index, query("newtag", ProjectSearchScope.ATTRIBUTES)))
                    .containsExactly("tree/note.md");
            assertThat(index.search(new ProjectSearchQuery("", ProjectSearchScope.ALL, "", Map.of("draft", "true"), 100)))
                    .extracting(ProjectSearchResult::title).containsExactly("After");

            Files.delete(note);
            index.refresh(note);
            assertThat(paths(index, query("newneedle", ProjectSearchScope.ALL))).isEmpty();
            assertThat(index.search(new ProjectSearchQuery("", ProjectSearchScope.ALL, "", Map.of("draft", "true"), 100))).isEmpty();
        }
    }

    @Test
    void deletingTreeDoesNotDeleteSimilarSearchPaths() throws Exception {
        Files.createDirectories(tmp.resolve("dir_name"));
        Files.createDirectories(tmp.resolve("dirXname"));
        Files.writeString(tmp.resolve("dir_name/a.md"), "sharedneedle");
        Files.writeString(tmp.resolve("dirXname/b.md"), "sharedneedle");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            Files.delete(tmp.resolve("dir_name/a.md"));
            Files.delete(tmp.resolve("dir_name"));
            index.refresh(tmp.resolve("dir_name"));
            assertThat(paths(index, query("sharedneedle", ProjectSearchScope.ALL)))
                    .containsExactly("dirXname/b.md");
        }
    }

    @Test
    void deletedLuceneCacheIsRecreatedFromMarkdown() throws Exception {
        Files.writeString(tmp.resolve("portable.md"), "# Portable\nsearchcache");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            assertThat(paths(index, query("searchcache", ProjectSearchScope.ALL))).containsExactly("portable.md");
        }
        try (var paths = Files.walk(ProjectPaths.luceneDir(tmp))) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            assertThat(paths(index, query("searchcache", ProjectSearchScope.ALL))).containsExactly("portable.md");
        }
    }

    @Test
    void contentScopeSkipsBomAndCrlfFrontMatter() throws Exception {
        Files.writeString(tmp.resolve("crlf.md"),
                "\uFEFF---\r\ntitle: Metadata only\r\ntags: [java]\r\n...\r\nbodyneedle\r\n");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            assertThat(paths(index, query("bodyneedle", ProjectSearchScope.CONTENT)))
                    .containsExactly("crlf.md");
            assertThat(paths(index, query("Metadata", ProjectSearchScope.CONTENT))).isEmpty();
            assertThat(paths(index, query("Metadata", ProjectSearchScope.TITLE)))
                    .containsExactly("crlf.md");
        }
    }

    @Test
    void malformedYamlStillIndexesContentAndUsesSafeMetadataDefaults() throws Exception {
        Files.writeString(tmp.resolve("broken.md"), "---\ntags: [\ndraft: [\n---\nresilientneedle");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            assertThat(paths(index, query("resilientneedle", ProjectSearchScope.CONTENT)))
                    .containsExactly("broken.md");
            assertThat(index.search(new ProjectSearchQuery("", ProjectSearchScope.ALL, "", Map.of("draft", "false"), 100)))
                    .extracting(ProjectSearchResult::path).containsExactly("broken.md");
            assertThat(paths(index, query("anything", ProjectSearchScope.ATTRIBUTES))).isEmpty();
        }
    }

    private static ProjectSearchQuery query(String text, ProjectSearchScope scope) {
        return new ProjectSearchQuery(text, scope, "", Map.of(), 100);
    }

    private static List<String> paths(ProjectIndex index, ProjectSearchQuery query) throws Exception {
        return index.search(query).stream().map(ProjectSearchResult::path).toList();
    }
}
