package ru.mcs.plugin.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class FrontMatterTest {

    @Test
    void absentFrontMatterIsNotCreatedForEmptyValues() {
        String source = "\uFEFF# Body\r\n";

        FrontMatter document = FrontMatter.parse(source);

        assertThat(document.present()).isFalse();
        assertThat(document.values()).isEqualTo(FrontMatter.Values.empty());
        assertThat(document.update(FrontMatter.Values.empty())).isSameAs(source);
    }

    @Test
    void createsFrontMatterWithBomCrlfAndExactBody() {
        String source = "\uFEFF# Body\r\nLine two\r\n";

        String updated = FrontMatter.parse(source).update(new FrontMatter.Values(
                "A \"quote\" \\ path", List.of("one", "two"), true));

        assertThat(updated).isEqualTo("\uFEFF---\r\n"
                + "title: \"A \\\"quote\\\" \\\\ path\"\r\n"
                + "tags: [\"one\", \"two\"]\r\n"
                + "draft: true\r\n"
                + "---\r\n"
                + "# Body\r\nLine two\r\n");
        assertThat(FrontMatter.parse(updated).values()).isEqualTo(new FrontMatter.Values(
                "A \"quote\" \\ path", List.of("one", "two"), true));
    }

    @Test
    void updatesOnlyManagedLinesAndPreservesUnknownYamlBodyAndClosingDelimiter() {
        String source = "\uFEFF---\r\n"
                + "title: Old\r\n"
                + "author: \"Unknown\"\r\n"
                + "tags: [one, 'two', \"three\"]\r\n"
                + "custom:\r\n"
                + "  nested: keep\r\n"
                + "draft: false\r\n"
                + "...\r\n"
                + "# Body\r\n---\r\n";

        FrontMatter document = FrontMatter.parse(source);
        assertThat(document.values()).isEqualTo(new FrontMatter.Values("Old", List.of("one", "two", "three"), false));
        assertThat(document.unknownYaml()).contains("author: \"Unknown\"", "custom:", "  nested: keep");

        String updated = document.update(new FrontMatter.Values("New", List.of(), true));

        assertThat(updated).isEqualTo("\uFEFF---\r\n"
                + "title: \"New\"\r\n"
                + "author: \"Unknown\"\r\n"
                + "custom:\r\n"
                + "  nested: keep\r\n"
                + "draft: true\r\n"
                + "...\r\n"
                + "# Body\r\n---\r\n");
    }

    @Test
    void preservesDashListStyleAndInsertsMissingManagedKeysBeforeClosing() {
        String source = "---\nauthor: Me\ntags:\n  - one\n  - 'two'\n---\nBody";

        String updated = FrontMatter.parse(source).update(new FrontMatter.Values(
                "Title", List.of("new tag", "x"), false));

        assertThat(updated).isEqualTo("---\n"
                + "author: Me\n"
                + "tags:\n"
                + "  - \"new tag\"\n"
                + "  - \"x\"\n"
                + "title: \"Title\"\n"
                + "---\nBody");
    }

    @Test
    void existingFalseDraftRemainsExplicitAndEmptyTitleIsRemoved() {
        String source = "---\ntitle: Remove\ndraft: false\n---\n";

        String updated = FrontMatter.parse(source).update(new FrontMatter.Values("", List.of(), false));

        assertThat(updated).isEqualTo("---\ndraft: false\n---\n");
    }

    @Test
    void parsesQuotedScalarsAndEmptyInlineList() {
        FrontMatter document = FrontMatter.parse("---\n"
                + "title: 'It''s simple'\n"
                + "tags: [ ]\n"
                + "draft: TRUE\n"
                + "---\n");
        assertThat(document.values()).isEqualTo(new FrontMatter.Values("It's simple", List.of(), true));

        FrontMatter escaped = FrontMatter.parse("---\ntitle: \"line\\n\\t\\u0041\"\n---\n");
        assertThat(escaped.values().title()).isEqualTo("line\n\tA");
    }

    @Test
    void acceptsTrailingCommaInInlineTags() {
        assertThat(FrontMatter.parse("---\ntags: [one, 'two',]\n---\n").values().tags())
                .containsExactly("one", "two");
    }

    @Test
    void rejectsUnsafeOrUnsupportedManagedYamlBeforeWriting() {
        assertThatIllegalArgumentException().isThrownBy(() -> FrontMatter.parse("---\ntitle: A\n"));
        assertThatIllegalArgumentException().isThrownBy(() -> FrontMatter.parse("---\ntitle: A\ntitle: B\n---\n"));
        assertThatIllegalArgumentException().isThrownBy(() -> FrontMatter.parse("---\ndraft: yes\n---\n"));
        assertThatIllegalArgumentException().isThrownBy(() -> FrontMatter.parse("---\ntitle: |\n  text\n---\n"));
        assertThatIllegalArgumentException().isThrownBy(() -> FrontMatter.parse("---\ntags: one\n---\n"));
        assertThatIllegalArgumentException().isThrownBy(() -> FrontMatter.parse("---\ntags:\n  nested: no\n---\n"));
    }

    @Test
    void quoteEscapesControlsAndRoundTrips() {
        String value = "a\\b\"c\n\r\t\u0001";

        FrontMatter document = FrontMatter.parse("---\ntitle: " + FrontMatter.quote(value) + "\n---\n");

        assertThat(document.values().title()).isEqualTo(value);
    }
}
