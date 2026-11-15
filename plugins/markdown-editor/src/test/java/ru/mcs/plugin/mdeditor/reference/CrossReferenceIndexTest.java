package ru.mcs.plugin.mdeditor.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrossReferenceIndexTest {

    @Test
    void numbersKindsIndependentlyAndResolvesReferences() {
        String source = "# Intro {#sec:intro}\n\n![A](a.png){#fig:a}\n"
                + "| A |\n|---|\n| 1 |\n{#tbl:data}\n\nSee #sec:intro, #fig:a and #tbl:data.";

        CrossReferenceIndex index = CrossReferenceIndex.scan(source);

        assertThat(index.reference("sec:intro")).isEqualTo("Section 1");
        assertThat(index.reference("fig:a")).isEqualTo("Figure 1");
        assertThat(index.reference("tbl:data")).isEqualTo("Table 1");
        assertThat(index.reference("fig:missing")).isNull();
        assertThat(index.labelAt(0)).isEqualTo("Section 1.");
        assertThat(index.labelInRange(source.indexOf("![A]"), source.indexOf("| A |")))
                .isEqualTo("Figure 1");
    }

    @Test
    void ignoresFrontMatterAndFencedCode() {
        String source = "---\nanchor: '{#fig:metadata}'\n---\n"
                + "```md\n{#sec:code}\n```\n~~~~\n{#tbl:code}\n~~~~\n"
                + "# Real {#sec:real}\n![R](r.png){#fig:real}";

        CrossReferenceIndex index = CrossReferenceIndex.scan(source);

        assertThat(index.reference("sec:metadata")).isNull();
        assertThat(index.reference("fig:metadata")).isNull();
        assertThat(index.reference("sec:code")).isNull();
        assertThat(index.reference("tbl:code")).isNull();
        assertThat(index.reference("sec:real")).isEqualTo("Section 1");
        assertThat(index.reference("fig:real")).isEqualTo("Figure 1");
    }

    @Test
    void firstDuplicateWinsWithoutSkippingFollowingNumbers() {
        String source = "![A](a.png){#fig:same}\n![B](b.png){#fig:same}\n"
                + "![C](c.png){#fig:next}";

        CrossReferenceIndex index = CrossReferenceIndex.scan(source);

        assertThat(index.reference("fig:same")).isEqualTo("Figure 1");
        assertThat(index.reference("fig:next")).isEqualTo("Figure 2");
    }
}
