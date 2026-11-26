package ru.mcs.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.view.HighlightSpan;

class YamlSyntaxHighlighterTest {

    private static List<String> tokens(String text) {
        return new YamlSyntaxHighlighter().highlight(text).stream()
                .map(span -> span.token().substring(5) + ":" + text.substring(span.start(), span.end())).toList();
    }

    @Test
    void highlightsCommentsKeysAndPlainValuesStayUncolored() {
        assertThat(tokens("# note\nname: Foo\n")).containsExactly("comment:# note", "key:name", "punct::");
    }

    @Test
    void numbersAndConstants() {
        assertThat(tokens("count: 42\nflag: true\nnothing: ~\nver: 1.2.3\n")).containsExactly(
                "key:count", "punct::", "number:42", "key:flag", "punct::", "constant:true",
                "key:nothing", "punct::", "constant:~", "key:ver", "punct::");
    }

    @Test
    void sequencesQuotedStringsAndTrailingComments() {
        assertThat(tokens("- a\n- key: 'x y'  # tail\n")).containsExactly(
                "punct:-", "punct:-", "key:key", "punct::", "string:'x y'", "comment:# tail");
        assertThat(tokens("\"a b\": 1")).containsExactly("key:\"a b\"", "punct::", "number:1");
    }

    @Test
    void flowCollections() {
        assertThat(tokens("list: [1, \"two\", null]")).containsExactly(
                "key:list", "punct::", "punct:[", "number:1", "punct:,", "string:\"two\"", "punct:,",
                "constant:null", "punct:]");
    }

    @Test
    void blockScalarLinesAreStringsUntilTheIndentEnds() {
        assertThat(tokens("text: |\n  not: a key\n  # not comment\nnext: 1\n")).containsExactly(
                "key:text", "punct::", "punct:|", "string:not: a key", "string:# not comment",
                "key:next", "punct::", "number:1");
    }

    @Test
    void anchorsTagsAndDocumentMarkers() {
        assertThat(tokens("base: &b !!map\nuse: *b\n")).containsExactly(
                "key:base", "punct::", "anchor:&b", "tag:!!map", "key:use", "punct::", "anchor:*b");
        assertThat(tokens("---\na: 1\n...\n")).containsExactly(
                "marker:---", "key:a", "punct::", "number:1", "marker:...");
    }

    @Test
    void hashInsideAWordIsNotAComment() {
        assertThat(tokens("url: http://x.org/a#frag # c")).containsExactly("key:url", "punct::", "comment:# c");
    }

    @Test
    void sampleCoversEveryTokenWithSortedNonOverlappingSpans() {
        List<HighlightSpan> spans = new YamlSyntaxHighlighter().highlight(YamlColorScheme.SAMPLE);

        assertThat(spans).extracting(HighlightSpan::token).containsAll(YamlTokens.ALL);
        int previousEnd = 0;
        for (HighlightSpan span : spans) {
            assertThat(span.start()).isGreaterThanOrEqualTo(previousEnd);
            previousEnd = span.end();
        }
    }
}
