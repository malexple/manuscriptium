package ru.mcs.markdown.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.view.HighlightSpan;

class MarkdownSyntaxHighlighterTest {

    private final MarkdownSyntaxHighlighter highlighter = new MarkdownSyntaxHighlighter();

    private List<String> show(String text) {
        return highlighter.highlight(text).stream()
                .map(span -> span.token() + "=" + text.substring(span.start(), span.end())).toList();
    }

    @Test
    void headingsAndInlineMarkup() {
        assertThat(show("# Title\n\ntext **bold** and *it* `code`\n")).containsExactly(
                "md.heading.marker=#", "md.heading=Title", "md.bold=**bold**", "md.italic=*it*", "md.code=`code`");
    }

    @Test
    void fencedCodeIsNotHighlightedInside() {
        assertThat(show("```java\nint x = 1;\n```\nafter *x*")).containsExactly(
                "md.fence=```java", "md.codeblock=int x = 1;", "md.fence=```", "md.italic=*x*");
        assertThat(show("```\n*not italic*\n```")).containsExactly(
                "md.fence=```", "md.codeblock=*not italic*", "md.fence=```");
    }

    @Test
    void frontMatterOnlyAtDocumentStartAndRuleElsewhere() {
        assertThat(show("---\ntitle: a\n---\n# H")).containsExactly("md.frontmatter=---",
                "md.frontmatter=title: a", "md.frontmatter=---", "md.heading.marker=#", "md.heading=H");
        assertThat(show("text\n\n---\n")).containsExactly("md.hr=---");
    }

    @Test
    void listsTasksAndQuotes() {
        assertThat(show("- [ ] todo\n1. one\n> quote **b**")).containsExactly(
                "md.list=-", "md.task=[ ]", "md.list=1.", "md.quote=>", "md.bold=**b**");
    }

    @Test
    void linksWikiLinksAndBareUrls() {
        assertThat(show("[text](http://x.com) and [[wiki]] and https://a.b/c")).containsExactly(
                "md.link.text=[text]", "md.link.url=(http://x.com)", "md.wikilink=[[wiki]]",
                "md.link.url=https://a.b/c");
    }

    @Test
    void tablesColorPipesAndSeparatorRow() {
        assertThat(show("| a | b |\n|---|---|\n| 1 | 2 |")).containsExactly(
                "md.table=|", "md.table=|", "md.table=|", "md.table=|---|---|",
                "md.table=|", "md.table=|", "md.table=|");
    }

    @Test
    void escapedMarkersAndPipesInCodeAreNotTokens() {
        assertThat(show("\\*not italic\\*")).isEmpty();
        assertThat(show("a\\|b | `x|y`")).containsExactly("md.code=`x|y`");
    }

    @Test
    void windowsLineEndingsAreNotPartOfTokens() {
        assertThat(show("# A\r\ntext **b**\r\n")).containsExactly(
                "md.heading.marker=#", "md.heading=A", "md.bold=**b**");
    }

    @Test
    void spansAreSortedInsideTheTextAndNeverOverlap() {
        String text = "---\nk: v\n---\n# H1 ##\n> **q** `c`\n- [x] a [l](u) ~~s~~ $m$\n$$\nx\n$$\n"
                + "<!-- c\nmore -->\n| a | `b|c` |\n|:-|-:|\n```\ncode\n```\n[^1] <b>t</b> ![i](p) %%o%%\n";
        List<HighlightSpan> spans = highlighter.highlight(text);

        assertThat(spans).isNotEmpty();
        int previousEnd = 0;
        for (HighlightSpan span : spans) {
            assertThat(span.start()).isGreaterThanOrEqualTo(previousEnd);
            assertThat(span.end()).isGreaterThan(span.start()).isLessThanOrEqualTo(text.length());
            assertThat(MarkdownTokens.ALL).contains(span.token());
            previousEnd = span.end();
        }
    }

    @Test
    void paletteCoversEveryTokenForBothBackgrounds() {
        MarkdownTokenColors colors = new MarkdownTokenColors();

        for (String token : MarkdownTokens.ALL) {
            assertThat(colors.color(token, false)).as(token).isNotNull();
            assertThat(colors.color(token, true)).as(token).isNotNull();
        }
        assertThat(colors.color("unknown", false)).isNull();
    }
}
