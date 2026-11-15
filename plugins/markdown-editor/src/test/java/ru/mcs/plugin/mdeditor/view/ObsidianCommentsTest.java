package ru.mcs.plugin.mdeditor.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObsidianCommentsTest {

    @Test
    void findsExactUtf16RangesAndMultilineText() {
        String source = "before %%first%% after\n%%line one\nline two%%\nend";
        var comments = ObsidianComments.parse(source);

        assertThat(comments).hasSize(2);
        assertThat(source.substring(comments.get(0).start(), comments.get(0).end())).isEqualTo("%%first%%");
        assertThat(comments.get(0).text()).isEqualTo("first");
        assertThat(source.substring(comments.get(1).start(), comments.get(1).end()))
                .isEqualTo("%%line one\nline two%%");
        assertThat(comments.get(1).text()).isEqualTo("line one\nline two");
    }

    @Test
    void ignoresFrontMatterAndFencedCode() {
        String source = "---\nnote: %%metadata%%\n---\n```\n%%code%%\n```\n"
                + "~~~markdown\n%%also code%%\n~~~\n%%visible%%";

        assertThat(ObsidianComments.parse(source)).singleElement()
                .satisfies(comment -> assertThat(comment.text()).isEqualTo("visible"));
    }

    @Test
    void unclosedCommentDoesNotConsumeDocument() {
        assertThat(ObsidianComments.parse("before %% unfinished\nnext")).isEmpty();
    }
}
