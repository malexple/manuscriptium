package ru.mcs.markdown.format;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownReformatterTest {

    private static final CodeStyle DEFAULT = CodeStyle.defaults();

    private static CodeStyle wrap(int width, boolean keepLineBreaks) {
        return new CodeStyle(width, keepLineBreaks, true, true, 1, 1);
    }

    @Test
    void stripsTrailingSpacesKeepsHardBreaksAndCapsBlankLines() {
        assertThat(MarkdownReformatter.format("a  \nb \n\n\n\nc\n", DEFAULT)).isEqualTo("a  \nb\n\nc\n");
        assertThat(MarkdownReformatter.format("\n\nfirst", DEFAULT)).isEqualTo("first");
        assertThat(MarkdownReformatter.format("end\n\n\n", DEFAULT)).isEqualTo("end\n");
    }

    @Test
    void putsBlankLinesAroundIsolatedBlocksOnlyWhenAsked() {
        assertThat(MarkdownReformatter.format("text\n# H\nmore\n", DEFAULT)).isEqualTo("text\n\n# H\n\nmore\n");
        assertThat(MarkdownReformatter.format("para\n```\ncode\n```\nafter", DEFAULT))
                .isEqualTo("para\n\n```\ncode\n```\n\nafter");
        CodeStyle none = new CodeStyle(0, true, true, true, 1, 0);
        assertThat(MarkdownReformatter.format("text\n# H\nmore\n", none)).isEqualTo("text\n# H\nmore\n");
    }

    @Test
    void codeFrontMatterAndMathAreUntouched() {
        String code = "```\n  x  \n\n\n\ny\n```\n";
        assertThat(MarkdownReformatter.format(code, DEFAULT)).isEqualTo(code);
        assertThat(MarkdownReformatter.format("---\ntitle:   x  \n---\n# H\n", DEFAULT))
                .isEqualTo("---\ntitle:   x  \n---\n\n# H\n");
        String math = "$$\na   +   b  \n\n\n\nc\n$$\n";
        assertThat(MarkdownReformatter.format(math, DEFAULT)).isEqualTo(math);
    }

    @Test
    void alignsTablesAndKeepsAlignmentMarkers() {
        String table = "|a|b|\n|-|:-:|\n|long cell|x|\n";

        String formatted = MarkdownReformatter.format(table, DEFAULT);

        assertThat(formatted).isEqualTo("| a         |  b  |\n| --------- | :-: |\n| long cell |  x  |\n");
        assertThat(MarkdownReformatter.format(formatted, DEFAULT)).isEqualTo(formatted);
        CodeStyle off = new CodeStyle(0, true, true, false, 1, 1);
        assertThat(MarkdownReformatter.format(table, off)).isEqualTo(table);
    }

    @Test
    void wrapsParagraphsAtTheConfiguredWidth() {
        assertThat(MarkdownReformatter.format("aaa bbb ccc ddd eee fff ggg hhh\n", wrap(20, false)))
                .isEqualTo("aaa bbb ccc ddd eee\nfff ggg hhh\n");
        assertThat(MarkdownReformatter.format("aa\nbb\n", wrap(80, true))).isEqualTo("aa\nbb\n");
        assertThat(MarkdownReformatter.format("aa\nbb\n", wrap(80, false))).isEqualTo("aa bb\n");
    }

    @Test
    void keepLineBreaksOnlySplitsLongLines() {
        assertThat(MarkdownReformatter.format("short\naaa bbb ccc ddd eee fff\n", wrap(14, true)))
                .isEqualTo("short\naaa bbb ccc\nddd eee fff\n");
    }

    @Test
    void listItemsGetHangingIndentAndQuotesKeepTheirPrefix() {
        assertThat(MarkdownReformatter.format("- aaa bbb ccc ddd eee fff", wrap(14, false)))
                .isEqualTo("- aaa bbb ccc\n  ddd eee fff");
        assertThat(MarkdownReformatter.format("> aaa bbb ccc ddd", wrap(12, false)))
                .isEqualTo("> aaa bbb\n> ccc ddd");
        CodeStyle noQuotes = new CodeStyle(12, false, false, true, 1, 1);
        assertThat(MarkdownReformatter.format("> aaa bbb ccc ddd", noQuotes)).isEqualTo("> aaa bbb ccc ddd");
    }

    @Test
    void neverBreaksAtomicSpansOrChangesBlockMeaning() {
        String wiki = MarkdownReformatter.format("see [[a b c]] now ok", wrap(12, false));
        assertThat(wiki).isEqualTo("see\n[[a b c]]\nnow ok");

        String guarded = MarkdownReformatter.format("aaaa bbbb - cccc", wrap(9, false));
        assertThat(guarded.lines()).noneMatch(line -> line.startsWith("-"));
    }

    @Test
    void headingsRulesSetextAndHtmlAreNotReflowed() {
        String setext = "Title\n=====\ntext\n";
        assertThat(MarkdownReformatter.format(setext, wrap(80, false))).isEqualTo(setext);
        String rule = "a\n---\nb";
        assertThat(MarkdownReformatter.format(rule, wrap(80, false))).isEqualTo(rule);
        String html = "<div>one\ntwo</div>\n";
        assertThat(MarkdownReformatter.format(html, wrap(80, false))).isEqualTo(html);
        String hard = "one  \ntwo\n";
        assertThat(MarkdownReformatter.format(hard, wrap(80, false))).isEqualTo(hard);
    }

    @Test
    void resultIsStableAndKeepsWindowsLineEndings() {
        String mixed = "# T\ntext  \nmore text here\n- item one two three\n\n\n|a|b|\n|-|-|\n|1|2|\n";
        CodeStyle style = wrap(16, false);

        String once = MarkdownReformatter.format(mixed, style);

        assertThat(MarkdownReformatter.format(once, style)).isEqualTo(once);
        assertThat(MarkdownReformatter.format("a \r\n\r\n\r\nb\r\n", DEFAULT)).isEqualTo("a\r\n\r\nb\r\n");
        assertThat(MarkdownReformatter.format("", DEFAULT)).isEmpty();
    }
}
