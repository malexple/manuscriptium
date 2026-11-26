package ru.mcs.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommentTogglerTest {

    @Test
    void commentsAndUncommentsPreservingIndentation() {
        assertThat(CommentToggler.toggle("a: 1\nb: 2")).isEqualTo("# a: 1\n# b: 2");
        assertThat(CommentToggler.toggle("# a: 1\n# b: 2")).isEqualTo("a: 1\nb: 2");
        String indented = "  a\n    b";
        String commented = CommentToggler.toggle(indented);
        assertThat(commented).isEqualTo("  # a\n  #   b");
        assertThat(CommentToggler.toggle(commented)).isEqualTo(indented);
    }

    @Test
    void blankLinesAreKeptAndMixedBlocksGetCommented() {
        assertThat(CommentToggler.toggle("a\n\n# b")).isEqualTo("# a\n\n# # b");
        assertThat(CommentToggler.toggle("\n")).isEqualTo("\n");
        assertThat(CommentToggler.toggle("#x")).isEqualTo("x");
    }
}
