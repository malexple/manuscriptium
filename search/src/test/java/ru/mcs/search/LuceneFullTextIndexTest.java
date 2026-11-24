package ru.mcs.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.project.IndexedDocument;
import ru.mcs.spi.project.SearchHit;
import ru.mcs.spi.project.SearchQuery;
import ru.mcs.spi.project.SearchScope;

class LuceneFullTextIndexTest {

    @TempDir
    Path tmp;

    @Test
    void searchesUnicodeContentMetadataPathAndAttributes() throws Exception {
        try (LuceneFullTextIndex index = new LuceneFullTextIndex(tmp.resolve("lucene"))) {
            index.replaceAll(List.of(
                    doc("notes/java.md", "Java 21",
                            "title: \"Java 21\"\ntags: [java, backend]\ndraft: false\nauthor: Alice",
                            "Полнотекстовый Поиск и C++ без специального синтаксиса.",
                            Map.of("tags", "java\nbackend", "draft", "false")),
                    doc("notes/draft.md", "Черновик", "title: Черновик\ntags:\n  - notes\ndraft: true",
                            "secretneedle", Map.of("tags", "notes", "draft", "true")),
                    doc("root.md", "Root", "", "# Root\njava outside the notes subtree",
                            Map.of("draft", "false"))));

            assertThat(paths(index, query("пОиСк", SearchScope.ALL))).containsExactly("notes/java.md");
            assertThat(paths(index, query("java", SearchScope.TITLE))).containsExactly("notes/java.md");
            assertThat(paths(index, query("Alice", SearchScope.METADATA))).containsExactly("notes/java.md");
            assertThat(paths(index, query("Alice", SearchScope.CONTENT))).isEmpty();
            assertThat(paths(index, query("backend", SearchScope.ATTRIBUTES))).containsExactly("notes/java.md");
            assertThat(paths(index, query("java.md", SearchScope.PATH))).containsExactly("notes/java.md");
            assertThat(paths(index, query("Поиск отсутствующий", SearchScope.ALL))).isEmpty();
            assertThat(paths(index, query("[::*", SearchScope.ALL))).isEmpty();
            assertThat(index.search(tmp, query("пОиСк", SearchScope.ALL)).getFirst().snippet())
                    .contains("Поиск");
            assertThat(index.search(tmp, query("пОиСк", SearchScope.ALL)).getFirst().file())
                    .isEqualTo(tmp.resolve("notes/java.md").normalize());

            SearchQuery notesOnly = new SearchQuery("java", SearchScope.ALL, "notes/", Map.of(), 100);
            assertThat(index.search(tmp, notesOnly)).extracting(SearchHit::path).containsExactly("notes/java.md");
            SearchQuery drafts = new SearchQuery("", SearchScope.ALL, "notes", Map.of("draft", "true"), 100);
            assertThat(index.search(tmp, drafts)).extracting(SearchHit::path).containsExactly("notes/draft.md");
            SearchQuery published = new SearchQuery("", SearchScope.ALL, "notes", Map.of("draft", "false"), 100);
            assertThat(index.search(tmp, published)).extracting(SearchHit::path).containsExactly("notes/java.md");
        }
    }

    @Test
    void incrementallyUpdatesAndRemovesDocuments() throws Exception {
        try (LuceneFullTextIndex index = new LuceneFullTextIndex(tmp.resolve("lucene"))) {
            index.replaceAll(List.of(doc("tree/note.md", "Before", "", "# Before\noldneedle", Map.of())));
            assertThat(paths(index, query("oldneedle", SearchScope.CONTENT))).containsExactly("tree/note.md");

            index.update(doc("tree/note.md", "After", "title: After\ntags: [newtag]\ndraft: true", "newneedle",
                    Map.of("tags", "newtag", "draft", "true")));
            assertThat(paths(index, query("oldneedle", SearchScope.ALL))).isEmpty();
            assertThat(paths(index, query("newneedle", SearchScope.CONTENT))).containsExactly("tree/note.md");
            assertThat(paths(index, query("newtag", SearchScope.ATTRIBUTES))).containsExactly("tree/note.md");
            assertThat(index.search(tmp, new SearchQuery("", SearchScope.ALL, "", Map.of("draft", "true"), 100)))
                    .extracting(SearchHit::title).containsExactly("After");

            index.removePath("tree/note.md");
            assertThat(paths(index, query("newneedle", SearchScope.ALL))).isEmpty();
            assertThat(index.search(tmp, new SearchQuery("", SearchScope.ALL, "", Map.of("draft", "true"), 100)))
                    .isEmpty();
        }
    }

    @Test
    void removingTreeDoesNotRemoveSimilarPaths() throws Exception {
        try (LuceneFullTextIndex index = new LuceneFullTextIndex(tmp.resolve("lucene"))) {
            index.replaceAll(List.of(
                    doc("dir_name/a.md", "a", "", "sharedneedle", Map.of()),
                    doc("dirXname/b.md", "b", "", "sharedneedle", Map.of())));
            index.removePath("dir_name");
            assertThat(paths(index, query("sharedneedle", SearchScope.ALL))).containsExactly("dirXname/b.md");
        }
    }

    @Test
    void deletedCacheIsRecreatedByReplacingAllDocuments() throws Exception {
        Path directory = tmp.resolve("lucene");
        List<IndexedDocument> documents = List.of(doc("portable.md", "Portable", "", "# Portable\nsearchcache", Map.of()));
        try (LuceneFullTextIndex index = new LuceneFullTextIndex(directory)) {
            index.replaceAll(documents);
            assertThat(paths(index, query("searchcache", SearchScope.ALL))).containsExactly("portable.md");
        }
        try (var walk = Files.walk(directory)) {
            for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
        try (LuceneFullTextIndex index = new LuceneFullTextIndex(directory)) {
            assertThat(paths(index, query("searchcache", SearchScope.ALL))).isEmpty();
            index.replaceAll(documents);
            assertThat(paths(index, query("searchcache", SearchScope.ALL))).containsExactly("portable.md");
        }
    }

    private static IndexedDocument doc(String path, String title, String metadata, String content,
                                       Map<String, String> attributes) {
        return new IndexedDocument(path, title, metadata, content, content, content.length(), 0L, attributes);
    }

    private static SearchQuery query(String text, SearchScope scope) {
        return new SearchQuery(text, scope, "", Map.of(), 100);
    }

    private List<String> paths(LuceneFullTextIndex index, SearchQuery query) throws Exception {
        return index.search(tmp, query).stream().map(SearchHit::path).toList();
    }
}
