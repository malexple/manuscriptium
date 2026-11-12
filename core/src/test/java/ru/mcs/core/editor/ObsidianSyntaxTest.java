package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObsidianSyntaxTest {

    @Test
    void parsesAliasesFragmentsEmbedsAndCalloutsWithExactOffsets() {
        String text = """
                ---
                title: "[[front-matter]]"
                ---
                Start [[Note]] and [[folder/Target.md#Heading Two|Alias]] ![[assets/pic.png|300]].
                `[[inline]]`
                ```
                [[fenced]]
                ```
                > [!warning]- Custom title
                > Body [[Note#^block-id]]
                """;

        ObsidianSyntax.Result result = ObsidianSyntax.parse(text);

        assertThat(result.links()).hasSize(4);
        ObsidianSyntax.Link note = result.links().get(0);
        assertThat(note.start()).isEqualTo(text.indexOf("[[Note]]"));
        assertThat(note.end()).isEqualTo(text.indexOf("[[Note]]") + "[[Note]]".length());
        assertThat(note.target()).isEqualTo("Note");
        assertThat(note.embed()).isFalse();

        ObsidianSyntax.Link target = result.links().get(1);
        assertThat(target.target()).isEqualTo("folder/Target.md");
        assertThat(target.fragment()).isEqualTo("Heading Two");
        assertThat(target.alias()).isEqualTo("Alias");
        assertThat(target.navigationTarget()).isEqualTo("folder/Target.md#Heading Two");

        ObsidianSyntax.Link image = result.links().get(2);
        assertThat(image.embed()).isTrue();
        assertThat(image.start()).isEqualTo(text.indexOf("![[assets/pic.png"));
        assertThat(text.substring(image.start(), image.end())).isEqualTo("![[assets/pic.png|300]]");
        assertThat(image.alias()).isEqualTo("300");

        ObsidianSyntax.Link block = result.links().get(3);
        assertThat(block.fragment()).isEqualTo("^block-id");
        assertThat(result.links()).noneMatch(link -> link.target().contains("front-matter")
                || link.target().contains("inline") || link.target().contains("fenced"));

        assertThat(result.callouts()).hasSize(1);
        ObsidianSyntax.Callout callout = result.callouts().getFirst();
        assertThat(callout.type()).isEqualTo("warning");
        assertThat(callout.title()).isEqualTo("Custom title");
        assertThat(callout.fold()).isEqualTo('-');
        assertThat(text.substring(callout.start(), callout.end())).startsWith("> [!warning]-")
                .endsWith("> Body [[Note#^block-id]]");
    }

    @Test
    void supportsLocalHeadingsTildeFencesAndMultilineInlineCodeDelimiters() {
        String text = """
                [[#Local heading|here]]
                ``code [[ignored]]``
                ~~~markdown
                ![[ignored.png]]
                ~~~~
                ![[visible.png]]
                [[|missing target]]
                [[unclosed
                """;

        ObsidianSyntax.Result result = ObsidianSyntax.parse(text);

        assertThat(result.links()).hasSize(2);
        assertThat(result.links().get(0).target()).isEmpty();
        assertThat(result.links().get(0).fragment()).isEqualTo("Local heading");
        assertThat(result.links().get(0).alias()).isEqualTo("here");
        assertThat(result.links().get(1).embed()).isTrue();
        assertThat(result.links().get(1).target()).isEqualTo("visible.png");
    }

    @Test
    void bomDoesNotTurnFrontMatterOpeningIntoClosingDelimiter() {
        String text = "\uFEFF---\ntitle: [[ignored]]\n---\n[[visible]]";

        ObsidianSyntax.Result result = ObsidianSyntax.parse(text);

        assertThat(result.links()).singleElement()
                .satisfies(link -> assertThat(link.target()).isEqualTo("visible"));
    }

    @Test
    void linkAtUsesUtf16DocumentOffsets() {
        String text = "😀 before [[Заметка]] after";
        ObsidianSyntax.Result result = ObsidianSyntax.parse(text);
        ObsidianSyntax.Link link = result.links().getFirst();

        assertThat(link.start()).isEqualTo(text.indexOf("[["));
        assertThat(result.linkAt(link.start())).contains(link);
        assertThat(result.linkAt(link.end() - 1)).contains(link);
        assertThat(result.linkAt(link.end())).isEmpty();
        assertThat(result.linkAt(0)).isEmpty();
    }
}
