package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceneVaultIndexTest {

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

        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(VaultPaths.luceneDir(tmp)).isDirectory();

            assertThat(paths(index, query("пОиСк", VaultSearchScope.ALL)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("java", VaultSearchScope.TITLE)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("Alice", VaultSearchScope.FRONT_MATTER)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("Alice", VaultSearchScope.CONTENT))).isEmpty();
            assertThat(paths(index, query("backend", VaultSearchScope.TAGS)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("java.md", VaultSearchScope.PATH)))
                    .containsExactly("notes/java.md");
            assertThat(paths(index, query("Поиск отсутствующий", VaultSearchScope.ALL))).isEmpty();
            assertThat(paths(index, query("[::*", VaultSearchScope.ALL))).isEmpty();
            assertThat(index.search(query("пОиСк", VaultSearchScope.ALL)).getFirst().snippet())
                    .contains("Поиск");

            VaultSearchQuery notesOnly = new VaultSearchQuery("java", VaultSearchScope.ALL,
                    "notes/", null, 100);
            assertThat(index.search(notesOnly)).extracting(VaultSearchResult::path)
                    .containsExactly("notes/java.md");
            VaultSearchQuery drafts = new VaultSearchQuery("", VaultSearchScope.ALL,
                    "notes", true, 100);
            assertThat(index.search(drafts)).extracting(VaultSearchResult::path)
                    .containsExactly("notes/draft.md");
            VaultSearchQuery published = new VaultSearchQuery("", VaultSearchScope.ALL,
                    "notes", false, 100);
            assertThat(index.search(published)).extracting(VaultSearchResult::path)
                    .containsExactly("notes/java.md");
        }
    }

    @Test
    void incrementallyUpdatesAndDeletesLuceneDocuments() throws Exception {
        Files.createDirectories(tmp.resolve("tree"));
        Path note = tmp.resolve("tree/note.md");
        Files.writeString(note, "# Before\noldneedle");
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(paths(index, query("oldneedle", VaultSearchScope.CONTENT)))
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
            assertThat(paths(index, query("oldneedle", VaultSearchScope.ALL))).isEmpty();
            assertThat(paths(index, query("newneedle", VaultSearchScope.CONTENT)))
                    .containsExactly("tree/note.md");
            assertThat(paths(index, query("newtag", VaultSearchScope.TAGS)))
                    .containsExactly("tree/note.md");
            assertThat(index.search(new VaultSearchQuery("", VaultSearchScope.ALL, "", true, 100)))
                    .extracting(VaultSearchResult::title).containsExactly("After");

            Files.delete(note);
            index.refresh(note);
            assertThat(paths(index, query("newneedle", VaultSearchScope.ALL))).isEmpty();
            assertThat(index.search(new VaultSearchQuery("", VaultSearchScope.ALL, "", true, 100))).isEmpty();
        }
    }

    @Test
    void deletingTreeDoesNotDeleteSimilarSearchPaths() throws Exception {
        Files.createDirectories(tmp.resolve("dir_name"));
        Files.createDirectories(tmp.resolve("dirXname"));
        Files.writeString(tmp.resolve("dir_name/a.md"), "sharedneedle");
        Files.writeString(tmp.resolve("dirXname/b.md"), "sharedneedle");
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            Files.delete(tmp.resolve("dir_name/a.md"));
            Files.delete(tmp.resolve("dir_name"));
            index.refresh(tmp.resolve("dir_name"));
            assertThat(paths(index, query("sharedneedle", VaultSearchScope.ALL)))
                    .containsExactly("dirXname/b.md");
        }
    }

    @Test
    void deletedLuceneCacheIsRecreatedFromMarkdown() throws Exception {
        Files.writeString(tmp.resolve("portable.md"), "# Portable\nsearchcache");
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(paths(index, query("searchcache", VaultSearchScope.ALL))).containsExactly("portable.md");
        }
        try (var paths = Files.walk(VaultPaths.luceneDir(tmp))) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(paths(index, query("searchcache", VaultSearchScope.ALL))).containsExactly("portable.md");
        }
    }

    @Test
    void contentScopeSkipsBomAndCrlfFrontMatter() throws Exception {
        Files.writeString(tmp.resolve("crlf.md"),
                "\uFEFF---\r\ntitle: Metadata only\r\ntags: [java]\r\n...\r\nbodyneedle\r\n");
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(paths(index, query("bodyneedle", VaultSearchScope.CONTENT)))
                    .containsExactly("crlf.md");
            assertThat(paths(index, query("Metadata", VaultSearchScope.CONTENT))).isEmpty();
            assertThat(paths(index, query("Metadata", VaultSearchScope.TITLE)))
                    .containsExactly("crlf.md");
        }
    }

    @Test
    void malformedYamlStillIndexesContentAndUsesSafeMetadataDefaults() throws Exception {
        Files.writeString(tmp.resolve("broken.md"), "---\ntags: [\ndraft: [\n---\nresilientneedle");
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(paths(index, query("resilientneedle", VaultSearchScope.CONTENT)))
                    .containsExactly("broken.md");
            assertThat(index.search(new VaultSearchQuery("", VaultSearchScope.ALL, "", false, 100)))
                    .extracting(VaultSearchResult::path).containsExactly("broken.md");
            assertThat(paths(index, query("anything", VaultSearchScope.TAGS))).isEmpty();
        }
    }

    private static VaultSearchQuery query(String text, VaultSearchScope scope) {
        return new VaultSearchQuery(text, scope, "", null, 100);
    }

    private static List<String> paths(VaultIndex index, VaultSearchQuery query) throws Exception {
        return index.search(query).stream().map(VaultSearchResult::path).toList();
    }
}
