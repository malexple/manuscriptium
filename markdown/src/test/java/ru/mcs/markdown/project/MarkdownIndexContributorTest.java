package ru.mcs.markdown.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.project.IndexedDocument;

class MarkdownIndexContributorTest {

    @TempDir
    Path root;

    private final MarkdownIndexContributor contributor = new MarkdownIndexContributor();

    @Test
    void extractsTitleBodyTagsAndDraftForGenericIndex() throws Exception {
        Path file = Files.writeString(root.resolve("article.md"), """
                ---
                title: 'Java 21'
                tags: [java, backend]
                draft: true
                author: Alice
                ---
                Body text
                """);

        IndexedDocument document = contributor.contribute(root, file, Files.readString(file), 10, 20);

        assertThat(document.path()).isEqualTo("article.md");
        assertThat(document.title()).isEqualTo("Java 21");
        assertThat(document.metadata()).contains("author: Alice");
        assertThat(document.content()).endsWith("Body text\n");
        assertThat(document.searchContent()).isEqualTo("Body text\n");
        assertThat(document.attributes()).containsEntry("tags", "java\nbackend").containsEntry("draft", "true");
        assertThat(document.size()).isEqualTo(10);
        assertThat(document.modifiedMs()).isEqualTo(20);
    }

    @Test
    void skipsBomAndCrlfMetadataInSearchContent() throws Exception {
        Path file = root.resolve("crlf.md");
        String text = "\uFEFF---\r\ntitle: Metadata only\r\ntags: [java]\r\n...\r\nbodyneedle\r\n";
        Files.writeString(file, text);

        IndexedDocument document = contributor.contribute(root, file, text, 1, 2);

        assertThat(document.title()).isEqualTo("Metadata only");
        assertThat(document.searchContent()).isEqualTo("bodyneedle\r\n");
    }

    @Test
    void malformedYamlKeepsBodyAndSafeAttributeDefaults() throws Exception {
        Path file = root.resolve("broken.md");
        String text = "---\ntags: [\ndraft: [\n---\nresilientneedle";
        Files.writeString(file, text);

        IndexedDocument document = contributor.contribute(root, file, text, 1, 2);

        assertThat(document.title()).isEqualTo("broken");
        assertThat(document.searchContent()).isEqualTo("resilientneedle");
        assertThat(document.attributes()).containsEntry("draft", "false").doesNotContainKey("tags");
    }

    @Test
    void headingAndFilenameAreTitleFallbacks() throws Exception {
        Path heading = root.resolve("heading.md");
        String headingText = "# Root heading\nText";
        Files.writeString(heading, headingText);
        assertThat(contributor.contribute(root, heading, headingText, 1, 2).title()).isEqualTo("Root heading");

        Path plain = root.resolve("plain.md");
        Files.writeString(plain, "Text");
        assertThat(contributor.contribute(root, plain, "Text", 1, 2).title()).isEqualTo("plain");
        assertThat(contributor.supports(root.resolve("NOTE.MD"))).isTrue();
        assertThat(contributor.supports(root.resolve("note.txt"))).isFalse();
    }
}
