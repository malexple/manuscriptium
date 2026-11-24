package ru.mcs.core.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.project.IndexedDocument;

/** Языковой вклад и каталог ядра: front matter, BOM/CRLF и битый YAML (раньше проверялось через Lucene). */
class ProjectIndexContributionTest {

    @TempDir
    Path tmp;

    @Test
    void parsesTitleTagsAndDraftFromFrontMatter() throws Exception {
        Files.writeString(tmp.resolve("java.md"),
                "---\ntitle: \"Java 21\"\ntags: [java, backend]\ndraft: false\nauthor: Alice\n---\nbodyneedle\n");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            IndexedDocument document = index.find("java.md").orElseThrow();
            assertThat(document.title()).isEqualTo("Java 21");
            assertThat(document.metadata()).contains("Alice");
            assertThat(document.searchContent()).contains("bodyneedle").doesNotContain("Alice");
            assertThat(document.attributes()).containsEntry("tags", "java\nbackend").containsEntry("draft", "false");
        }
    }

    @Test
    void contentSkipsBomAndCrlfFrontMatter() throws Exception {
        Files.writeString(tmp.resolve("crlf.md"),
                "\uFEFF---\r\ntitle: Metadata only\r\ntags: [java]\r\n...\r\nbodyneedle\r\n");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            IndexedDocument document = index.find("crlf.md").orElseThrow();
            assertThat(document.title()).isEqualTo("Metadata only");
            assertThat(document.searchContent()).contains("bodyneedle").doesNotContain("Metadata");
        }
    }

    @Test
    void malformedYamlStillIndexesContentAndUsesSafeDefaults() throws Exception {
        Files.writeString(tmp.resolve("broken.md"), "---\ntags: [\ndraft: [\n---\nresilientneedle");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            IndexedDocument document = index.find("broken.md").orElseThrow();
            assertThat(document.searchContent()).contains("resilientneedle");
            assertThat(document.attributes()).containsEntry("draft", "false").doesNotContainKey("tags");
        }
    }

    @Test
    void refreshAndRemovalKeepCatalogConsistentWithoutFullTextIndex() throws Exception {
        Files.createDirectories(tmp.resolve("dir_name"));
        Files.createDirectories(tmp.resolve("dirXname"));
        Files.writeString(tmp.resolve("dir_name/a.md"), "sharedneedle");
        Files.writeString(tmp.resolve("dirXname/b.md"), "sharedneedle");
        try (ProjectIndex index = new ProjectIndex(tmp, TestIndexContributor.registry())) {
            index.rebuild();
            Files.delete(tmp.resolve("dir_name/a.md"));
            Files.delete(tmp.resolve("dir_name"));
            index.refresh(tmp.resolve("dir_name"));
            assertThat(index.notes()).extracting(ProjectNote::path).containsExactly("dirXname/b.md");
            assertThat(index.search(new ru.mcs.spi.project.SearchQuery("sharedneedle", null, "", null, 100))).isEmpty();
        }
    }
}
