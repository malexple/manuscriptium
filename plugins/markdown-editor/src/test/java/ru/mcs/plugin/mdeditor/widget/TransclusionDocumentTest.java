package ru.mcs.plugin.mdeditor.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TransclusionDocumentTest {

    @Test
    void wholeNoteMergeReplacesOnlyEditableDocument() {
        TransclusionDocument document = TransclusionDocument.whole("# Chapter\nBody\n");

        assertThat(document.prefix()).isEmpty();
        assertThat(document.editable()).isEqualTo("# Chapter\nBody\n");
        assertThat(document.suffix()).isEmpty();
        assertThat(document.merge("Changed\n")).isEqualTo("Changed\n");
    }

    @Test
    void sectionIncludesNestedHeadingsAndStopsAtSameOrHigherLevel() {
        String source = "# Book\n\n## Target section\nfirst\n### Child\nchild body\n"
                + "## Next\nuntouched\n# End\n";
        TransclusionDocument document = TransclusionDocument.section(source, "target-section");

        assertThat(document.editable())
                .isEqualTo("## Target section\nfirst\n### Child\nchild body\n");
        assertThat(document.prefix()).isEqualTo("# Book\n\n");
        assertThat(document.suffix()).isEqualTo("## Next\nuntouched\n# End\n");
        assertThat(document.merge("## Target section\nchanged\n"))
                .isEqualTo("# Book\n\n## Target section\nchanged\n## Next\nuntouched\n# End\n");
    }

    @Test
    void sectionPreservesCrLfPrefixAndSuffixExactly() {
        String source = "intro\r\n## Два слова\r\nold\r\n### inner\r\nbody\r\n# tail\r\n";
        TransclusionDocument document = TransclusionDocument.section(source, "Два слова");

        assertThat(document.merge("## Два слова\r\nnew\r\n"))
                .isEqualTo("intro\r\n## Два слова\r\nnew\r\n# tail\r\n");
    }

    @Test
    void missingOrEmptyFragmentIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> TransclusionDocument.section("# One", ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TransclusionDocument.section("# One", "missing"));
    }
}
