package ru.mcs.plugin.mdparser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.editor.MarkdownParser.Change;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;

class FlexmarkMarkdownParserTest {

    private final MarkdownParser parser = new FlexmarkMarkdownParser();

    @Test
    void parsesExtensionsAndPreservesExactUtf16SourceSpans() {
        String markdown = """
                ---
                title: Demo
                ---
                # Heading

                > [!NOTE]- Folded [[Note|Alias]] ![[image.png]]

                - [x] done
                - [ ] todo

                | A | B |
                |:---|---:|
                | ~~old~~ | $x+1$ |

                $$
                a^2
                $$

                Footnote[^1]

                [^1]: Definition
                """;
        ParseResult result = parser.parse(markdown, 7);

        assertThat(result.version()).isEqualTo(7);
        assertThat(result.spans()).extracting(BlockSpan::kind)
                .contains(BlockKind.DOCUMENT, BlockKind.FRONT_MATTER, BlockKind.HEADING,
                        BlockKind.CALLOUT, BlockKind.WIKILINK, BlockKind.EMBED, BlockKind.TASK_ITEM,
                        BlockKind.TABLE, BlockKind.STRIKETHROUGH, BlockKind.MATH_INLINE,
                        BlockKind.MATH_BLOCK, BlockKind.FOOTNOTE_REFERENCE, BlockKind.FOOTNOTE_DEFINITION);
        assertSource(result, BlockKind.CALLOUT, markdown, "> [!NOTE]- Folded");
        assertSource(result, BlockKind.WIKILINK, markdown, "[[Note|Alias]]");
        assertSource(result, BlockKind.EMBED, markdown, "![[image.png]]");
        assertSource(result, BlockKind.MATH_INLINE, markdown, "$x+1$");
        assertSource(result, BlockKind.MATH_BLOCK, markdown, "$$\na^2\n$$");
        assertSource(result, BlockKind.FOOTNOTE_REFERENCE, markdown, "[^1]");
        assertThat(spans(result, BlockKind.TASK_ITEM))
                .anySatisfy(span -> assertThat(span.attributes()).containsEntry("done", "true"))
                .anySatisfy(span -> assertThat(span.attributes()).containsEntry("done", "false"));
        assertThat(spans(result, BlockKind.CALLOUT).getFirst().attributes())
                .containsEntry("type", "note").containsEntry("fold", "-");
        assertAllOffsetsValid(result, markdown.length());
    }

    @Test
    void singleCharacterEditReparsesOnlyAffectedBlockAndMatchesFullParse() {
        String markdown = """
                alpha

                bravo

                charlie

                delta

                echo

                foxtrot
                """;
        ParseResult initial = parser.parse(markdown, 0);
        int offset = markdown.indexOf("delta") + 2;
        String changed = markdown.substring(0, offset) + "X" + markdown.substring(offset);

        ParseResult incremental = parser.reparse(initial, changed, 1, new Change(offset, 0, 1));
        ParseResult full = parser.parse(changed, 1);

        assertThat(incremental.spans()).isEqualTo(full.spans());
        assertThat(incremental.metrics().fullParse()).isFalse();
        assertThat(incremental.metrics().parsedBlocks()).isEqualTo(1);
        assertThat(incremental.metrics().reusedBlocks()).isEqualTo(5);
    }

    @Test
    void newlineAndUnclosedFenceMatchFullParse() {
        String markdown = "before\n\nafter\n\nlast\n\ntail";
        ParseResult initial = parser.parse(markdown, 0);
        int offset = markdown.indexOf("after");
        String inserted = "```java\n";
        String changed = markdown.substring(0, offset) + inserted + markdown.substring(offset);

        ParseResult incremental = parser.reparse(initial, changed, 1,
                new Change(offset, 0, inserted.length()));

        assertThat(incremental.spans()).isEqualTo(parser.parse(changed, 1).spans());
        assertThat(incremental.metrics().fullParse()).isFalse();
        assertThat(spans(incremental, BlockKind.FENCED_CODE).getFirst().attributes())
                .containsEntry("closed", "false");
    }

    @Test
    void randomizedIncrementalEditsAlwaysMatchFullAst() {
        String markdown = """
                # Title

                alpha **bold**

                > quote

                - one
                - two

                ```java
                code();
                ```

                omega

                final paragraph
                """;
        String alphabet = "ab XYZ\n`~|$[]";
        for (long seed : new long[]{0xE3L, 0xC0FFEEL, 0x5EEDL, 0xBAD5EEDL}) {
            Random random = new Random(seed);
            ParseResult current = parser.parse(markdown, 0);
            String text = markdown;
            for (int iteration = 1; iteration <= 250; iteration++) {
                int start = text.isEmpty() ? 0 : random.nextInt(text.length());
                int oldLength = text.isEmpty() ? 0 : random.nextInt(Math.min(4, text.length() - start) + 1);
                int newLength = random.nextInt(4);
                StringBuilder fragment = new StringBuilder();
                for (int i = 0; i < newLength; i++) {
                    fragment.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                String changed = text.substring(0, start) + fragment + text.substring(start + oldLength);
                current = parser.reparse(current, changed, iteration, new Change(start, oldLength, newLength));
                ParseResult expected = parser.parse(changed, iteration);
                assertThat(current.spans()).as("seed %s, iteration %s, change %s..%s +%s%nold=%s%nnew=%s%noldAst=%s%nmetrics=%s",
                        seed, iteration, start, oldLength, newLength, text.replace("\n", "\\n"),
                        changed.replace("\n", "\\n"), parser.parse(text, iteration - 1).spans(),
                        current.metrics()).isEqualTo(expected.spans());
                text = changed;
            }
        }
    }

    @Test
    void boundaryEditsMatchFullParseAndDoNotDropChangedPrefix() {
        String paragraphs = " \n Y\n~]t$aZ]Z X~X|b|\n\n*\nb[a[ e\nZ|Y  `\nZ[[Z~~n\n `rb";
        assertReparse(paragraphs, 20, 1, "");
        assertReparse("`[$ZtZ$|YYX `\nZX bY~`$|Y |`$|b$~~", 0, 2, "`[Y");
    }

    @Test
    void embedIncludesBangButEscapedEmbedRemainsWikiLinkAndOffsetsAreUtf16() {
        String markdown = "😀 ![[image.png]] \\![[escaped.png]]";
        ParseResult result = parser.parse(markdown, 0);

        assertThat(spans(result, BlockKind.EMBED)).singleElement()
                .satisfies(span -> assertThat(markdown.substring(span.start(), span.end()))
                        .isEqualTo("![[image.png]]"));
        assertThat(spans(result, BlockKind.WIKILINK)).singleElement()
                .satisfies(span -> assertThat(markdown.substring(span.start(), span.end()))
                        .isEqualTo("[[escaped.png]]"));
        assertThat(result.spans()).anySatisfy(span -> {
            if (span.kind() == BlockKind.TEXT && markdown.substring(span.start(), span.end()).contains("😀")) {
                assertThat(span.start()).isZero();
                assertThat(span.end()).isGreaterThanOrEqualTo(2);
            }
        });
    }

    @Test
    void introducingReferenceDefinitionUsesConservativeFullParse() {
        String markdown = "Reference [id]\n\n[id]";
        ParseResult initial = parser.parse(markdown, 0);
        String changed = markdown + ":";

        ParseResult incremental = parser.reparse(initial, changed, 1,
                new Change(markdown.length(), 0, 1));

        assertThat(incremental.metrics().fullParse()).isTrue();
        assertThat(incremental.spans()).isEqualTo(parser.parse(changed, 1).spans());
    }

    @Test
    void mathScannerDoesNotAffectCodeOrEscapedDollars() {
        String markdown = "Price \\$5 and ` $code$ `\n\n```\n$ fenced $\n```\n\n$real$";
        ParseResult result = parser.parse(markdown, 0);

        assertThat(spans(result, BlockKind.MATH_INLINE)).singleElement()
                .satisfies(span -> assertThat(markdown.substring(span.start(), span.end())).isEqualTo("$real$"));
    }

    private void assertReparse(String markdown, int start, int oldLength, String replacement) {
        ParseResult initial = parser.parse(markdown, 0);
        String changed = markdown.substring(0, start) + replacement + markdown.substring(start + oldLength);

        ParseResult incremental = parser.reparse(initial, changed, 1,
                new Change(start, oldLength, replacement.length()));

        assertThat(incremental.metrics().fullParse()).isFalse();
        assertThat(incremental.spans()).isEqualTo(parser.parse(changed, 1).spans());
    }

    private static void assertSource(ParseResult result, BlockKind kind, String source, String expectedPrefix) {
        assertThat(spans(result, kind)).anySatisfy(span ->
                assertThat(source.substring(span.start(), span.end())).startsWith(expectedPrefix));
    }

    private static List<BlockSpan> spans(ParseResult result, BlockKind kind) {
        List<BlockSpan> matches = new ArrayList<>();
        for (BlockSpan span : result.spans()) {
            if (span.kind() == kind) {
                matches.add(span);
            }
        }
        return matches;
    }

    private static void assertAllOffsetsValid(ParseResult result, int length) {
        for (BlockSpan span : result.spans()) {
            assertThat(span.start()).isBetween(0, length);
            assertThat(span.end()).isBetween(span.start(), length);
            assertThat(span.depth()).isNotNegative();
        }
    }
}
