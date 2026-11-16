package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.mcs.plugin.mdeditor.layout.BlockBox;
import ru.mcs.plugin.mdeditor.layout.InlineRun;
import ru.mcs.plugin.mdeditor.layout.LayoutResult;
import ru.mcs.plugin.mdeditor.layout.LineBox;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.Insert;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.model.Range;
import ru.mcs.plugin.mdeditor.render.MarkdownRenderer;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser.ParseMetrics;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;
import ru.mcs.spi.editor.style.TextDecoration;

class MarkdownLayoutEngineTest {

    @Test
    void rendersBlocksHidesMarkersAndKeepsOnlyActiveBlockAsSource() {
        String markdown = "# Title\n\n> Quote\n\n- one\n  - two\n- [ ] task\n\n---\n\n"
                + "```java\nclass A {}\n```\n\nActive";
        List<BlockSpan> blocks = List.of(
                span(BlockKind.HEADING, 0, 7, Map.of("level", "1")),
                span(BlockKind.BLOCK_QUOTE, 9, 16),
                span(BlockKind.BULLET_LIST, 18, 42),
                span(BlockKind.THEMATIC_BREAK, 44, 47),
                span(BlockKind.FENCED_CODE, 49, 71, Map.of("info", "java")),
                span(BlockKind.PARAGRAPH, 73, markdown.length()));
        MarkdownLayoutEngine engine = engine(markdown, blocks, 73, 800);

        LayoutResult result = engine.layout();
        assertThat(result.blocks()).hasSize(6);
        assertThat(result.blocks()).filteredOn(BlockBox::source).singleElement()
                .satisfies(block -> assertThat(block.span().kind()).isEqualTo(BlockKind.PARAGRAPH));
        assertThat(visibleText(result.blocks().get(0))).isEqualTo("Title");
        assertThat(visibleText(result.blocks().get(1))).isEqualTo("Quote");
        assertThat(visibleText(result.blocks().get(2))).contains("one", "two", "task").doesNotContain("- [");
        assertThat(result.blocks().get(2).lines().stream().flatMap(line -> line.runs().stream()))
                .anySatisfy(run -> {
                    assertThat(run.taskOffset()).isEqualTo(35);
                    assertThat(run.taskChecked()).isFalse();
                    assertThat(run.text()).isEqualTo("    ");
                });
        assertThat(result.blocks().get(3).lines().getFirst().runs()).isEmpty();
        assertThat(visibleText(result.blocks().get(4))).isEqualTo("class A {}");
        assertThat(visibleText(result.blocks().get(5))).isEqualTo("Active");
        assertThat(allText(result)).doesNotContain("# Title", "> Quote", "```", "class A {}\n```");
    }

    @Test
    void supportsAtxAndSetextHeadingLevels() {
        String markdown = "# ATX\n\nSetext\n---";
        List<BlockSpan> blocks = List.of(
                span(BlockKind.HEADING, 0, 5, Map.of("level", "1")),
                span(BlockKind.HEADING, 7, markdown.length(), Map.of("level", "2")));
        MarkdownLayoutEngine engine = engine(markdown, blocks, 0, 800);

        LayoutResult result = engine.layout();
        assertThat(result.blocks().get(0).source()).isTrue();
        assertThat(visibleText(result.blocks().get(1))).isEqualTo("Setext");
        assertThat(result.blocks().get(0).style().fontSizePx()).isEqualTo(32);
        assertThat(result.blocks().get(1).style().fontSizePx()).isEqualTo(28);
    }

    @Test
    void rendersInlineStylesLinksCodeAndUnicodeWithoutLosingSourceText() {
        String inline = "A **bold** *em* ~~del~~ `code` [link](https://example.com) 😀";
        String markdown = inline + "\n\nActive";
        List<BlockSpan> blocks = List.of(span(BlockKind.PARAGRAPH, 0, inline.length()),
                span(BlockKind.PARAGRAPH, inline.length() + 2, markdown.length()));
        MarkdownLayoutEngine engine = engine(markdown, blocks, markdown.length(), 1000);

        BlockBox block = engine.layout().blocks().getFirst();
        assertThat(visibleText(block)).isEqualTo("A bold em del code link 😀");
        List<InlineRun> runs = block.lines().stream().flatMap(line -> line.runs().stream()).toList();
        assertThat(runs).anySatisfy(run -> {
            assertThat(run.text()).isEqualTo("bold");
            assertThat(run.font().isBold()).isTrue();
        });
        assertThat(runs).anySatisfy(run -> {
            assertThat(run.text()).isEqualTo("em");
            assertThat(run.font().isItalic()).isTrue();
        });
        assertThat(runs).anySatisfy(run -> {
            assertThat(run.text()).isEqualTo("del");
            assertThat(run.style().textDecorations()).contains(TextDecoration.LINE_THROUGH);
        });
        assertThat(runs).anySatisfy(run -> {
            assertThat(run.text()).isEqualTo("code");
            assertThat(run.font().getFamily()).isEqualTo("Monospaced");
        });
        InlineRun link = runs.stream().filter(run -> "link".equals(run.text())).findFirst().orElseThrow();
        assertThat(link.linkTarget()).isEqualTo("https://example.com");
        LineBox linkLine = block.lines().stream().filter(line -> line.runs().contains(link)).findFirst().orElseThrow();
        assertThat(engine.linkAt(new Point(link.x() + 1, linkLine.y() + 2))).isEqualTo("https://example.com");
        assertThat(runs).anySatisfy(run -> assertThat(run.text()).isEqualTo("😀"));
    }

    @Test
    void caretAndHitTestRoundTripAcrossUnicodeAndWrappedLines() {
        String text = "Plain 😀 text with enough words to wrap across several visual lines in a narrow viewport";
        String markdown = text + "\n\nActive";
        List<BlockSpan> blocks = List.of(span(BlockKind.PARAGRAPH, 0, text.length()),
                span(BlockKind.PARAGRAPH, text.length() + 2, markdown.length()));
        MarkdownLayoutEngine engine = engine(markdown, blocks, markdown.length(), 260);

        assertThat(engine.layout().blocks().getFirst().lines()).hasSizeGreaterThan(1);
        for (int offset = 0; offset <= text.length();) {
            Rectangle caret = engine.caretRect(offset);
            int hit = engine.hitTest(new Point(caret.x, caret.y + Math.max(1, caret.height / 2)));
            assertThat(hit).as("round trip at source offset %s", offset).isEqualTo(offset);
            if (offset == text.length()) {
                break;
            }
            offset += Character.charCount(Character.codePointAt(text, offset));
        }
    }

    @Test
    void hiddenMarkersMapCaretToFullInlineConstructBoundaries() {
        String text = "before **bold** after";
        String markdown = text + "\n\nActive";
        List<BlockSpan> blocks = List.of(span(BlockKind.PARAGRAPH, 0, text.length()),
                span(BlockKind.PARAGRAPH, text.length() + 2, markdown.length()));
        MarkdownLayoutEngine engine = engine(markdown, blocks, markdown.length(), 800);

        InlineRun bold = engine.layout().blocks().getFirst().lines().stream()
                .flatMap(line -> line.runs().stream()).filter(run -> "bold".equals(run.text())).findFirst().orElseThrow();
        assertThat(bold.sourceStart()).isEqualTo(text.indexOf("**"));
        assertThat(bold.sourceEnd()).isEqualTo(text.indexOf("**", text.indexOf("**") + 2) + 2);
        assertThat(bold.sourceOffset(0)).isEqualTo(text.indexOf("**"));
        assertThat(bold.sourceOffset(1)).isEqualTo(text.indexOf("bold") + 1);
        assertThat(bold.sourceOffset(4)).isEqualTo(text.indexOf(" after"));
    }

    @Test
    void appliesWriteMaxWidthPaddingAndCentering() {
        String markdown = "one\n\nActive";
        MarkdownLayoutEngine engine = engine(markdown, List.of(span(BlockKind.PARAGRAPH, 0, 3),
                span(BlockKind.PARAGRAPH, 5, markdown.length())), markdown.length(), 1000);

        LayoutResult result = engine.layout();
        assertThat(result.writeWidth()).isEqualTo(640);
        assertThat(result.writeX()).isEqualTo(180);
        assertThat(result.blocks().getFirst().x()).isEqualTo(196);
        assertThat(result.blocks().getFirst().y()).isEqualTo(16);
    }

    @Test
    void cachesLayoutUntilDocumentWidthOrActiveBlockChanges() {
        String markdown = "one\n\ntwo";
        MarkdownLayoutEngine engine = engine(markdown, List.of(span(BlockKind.PARAGRAPH, 0, 3),
                span(BlockKind.PARAGRAPH, 5, markdown.length())), 0, 800);

        engine.layout();
        engine.layout();
        assertThat(engine.layoutCount()).isEqualTo(1);
        engine.setCaretOffset(1);
        engine.layout();
        assertThat(engine.layoutCount()).isEqualTo(1);
        engine.setCaretOffset(5);
        engine.layout();
        assertThat(engine.layoutCount()).isEqualTo(2);
        engine.setWidth(700);
        engine.layout();
        assertThat(engine.layoutCount()).isEqualTo(3);
    }

    @Test
    void staleAstFallsBackToCurrentDocumentSourceAndSelectionUsesSourceOffsets() {
        String markdown = "old **text**";
        DocumentModelImpl document = document(markdown);
        TestEditorStyleService styles = new TestEditorStyleService();
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, ast(markdown, 0, List.of(span(BlockKind.PARAGRAPH, 0, markdown.length()))), styles);
        engine.setWidth(500);
        document.apply(new Insert(0, "new\n\n"));

        LayoutResult result = engine.layout();
        assertThat(result.documentVersion()).isEqualTo(document.version());
        assertThat(result.blocks()).singleElement().satisfies(block -> {
            assertThat(block.source()).isTrue();
            assertThat(visibleText(block)).isEqualTo("new\n\nold **text**");
        });
        assertThat(engine.selectionRects(new Range(0, 4), new Rectangle(0, 0, 500, 500))).isNotEmpty();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void visibleFrameOnFiveThousandLinesStaysBelowSixteenMillisecondsAfterWarmup() {
        StringBuilder source = new StringBuilder();
        List<BlockSpan> blocks = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            int start = source.length();
            source.append("Line ").append(i).append(" with text");
            int end = source.length();
            blocks.add(span(BlockKind.PARAGRAPH, start, end));
            source.append("\n\n");
        }
        String markdown = source.toString();
        MarkdownLayoutEngine engine = engine(markdown, blocks, 0, 800);
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        MarkdownRenderer renderer = new MarkdownRenderer(engine);
        try {
            renderer.paint(graphics, new Rectangle(0, 0, 800, 600), new Range(0, 0));
            List<Long> frames = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                long started = System.nanoTime();
                renderer.paint(graphics, new Rectangle(0, 0, 800, 600), new Range(0, 0));
                frames.add(System.nanoTime() - started);
            }
            frames.sort(Long::compareTo);
            long p95Nanos = frames.get((int) Math.floor(frames.size() * 0.95));
            assertThat(p95Nanos).as("p95 visible frame time").isLessThan(16_000_000L);
            assertThat(engine.layoutCount()).isEqualTo(1);
        } finally {
            graphics.dispose();
        }
    }

    private static MarkdownLayoutEngine engine(String markdown, List<BlockSpan> blocks, int caret, int width) {
        DocumentModelImpl document = document(markdown);
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, ast(markdown, document.version(), blocks), new TestEditorStyleService());
        engine.setWidth(width);
        engine.setCaretOffset(caret);
        return engine;
    }

    private static DocumentModelImpl document(String markdown) {
        return new DocumentModelImpl(markdown, DocumentProperties.utf8(LineEnding.LF, false));
    }

    private static AstIndex ast(String markdown, long version, List<BlockSpan> blocks) {
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, markdown.length(), 0));
        spans.addAll(blocks);
        return new AstIndex(new Result(version, spans, new ParseMetrics(blocks.size(), 0, true)));
    }

    private static BlockSpan span(BlockKind kind, int start, int end) {
        return new BlockSpan(kind, start, end, 1);
    }

    private static BlockSpan span(BlockKind kind, int start, int end, Map<String, String> attributes) {
        return new BlockSpan(kind, start, end, 1, attributes);
    }

    private static String visibleText(BlockBox block) {
        StringBuilder result = new StringBuilder();
        for (LineBox line : block.lines()) {
            for (InlineRun run : line.runs()) {
                result.append(run.text());
            }
            result.append('\n');
        }
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '\n') {
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    private static String allText(LayoutResult result) {
        return result.blocks().stream().map(MarkdownLayoutEngineTest::visibleText).reduce("", (a, b) -> a + "\n" + b);
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
