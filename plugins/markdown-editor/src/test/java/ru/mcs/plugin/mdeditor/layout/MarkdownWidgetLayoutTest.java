package ru.mcs.plugin.mdeditor.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.TestEditorStyleService;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;
import ru.mcs.plugin.mdeditor.widget.CalloutWidget;
import ru.mcs.plugin.mdeditor.widget.FrontMatterWidget;
import ru.mcs.plugin.mdeditor.widget.MathWidget;
import ru.mcs.plugin.mdeditor.widget.TableWidget;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser.ParseMetrics;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;

class MarkdownWidgetLayoutTest {

    @Test
    void createsBlockWidgetsAndPreservesInlineMathSourceOffsets() {
        String markdown = "---\ntitle: Demo\n---\n\n"
                + "> [!note] Hint\n> Body\n\n"
                + "| A | B |\n|---|---|\n| 1 | 2 |\n\n"
                + "$$\nx^2\n$$\n\nInline $y + z$ end\n\nActive";
        List<BlockSpan> blocks = blocks(markdown);
        DocumentModelImpl document = new DocumentModelImpl(markdown,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
        WidgetRegistry widgets = new WidgetRegistry(Runnable::run,
                (latex, display, size, color) -> new BufferedImage(32, 18, BufferedImage.TYPE_INT_ARGB));
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, ast(markdown, blocks), new TestEditorStyleService());
        engine.setWidgets(widgets, null);
        engine.setWidth(800);
        engine.setCaretOffset(markdown.length());

        LayoutResult layout = engine.layout();
        assertThat(layout.blocks().stream().filter(block -> block.widget() instanceof FrontMatterWidget)).hasSize(1);
        assertThat(layout.blocks().stream().filter(block -> block.widget() instanceof CalloutWidget)).hasSize(1);
        assertThat(layout.blocks().stream().filter(block -> block.widget() instanceof TableWidget)).hasSize(1);
        assertThat(layout.blocks().stream().filter(block -> block.widget() instanceof MathWidget)).hasSize(1);
        List<InlineRun> mathRuns = layout.blocks().stream().filter(block -> block.span().kind() == BlockKind.PARAGRAPH)
                .flatMap(block -> block.lines().stream()).flatMap(line -> line.runs().stream())
                .filter(run -> run.image() != null).toList();
        assertThat(mathRuns).hasSize(1);
        InlineRun math = mathRuns.getFirst();
        assertThat(markdown.substring(math.sourceStart(), math.sourceEnd())).isEqualTo("$y + z$");
        assertThat(math.advance()).isEqualTo(32f);
        assertThat(math.caretX(math.sourceEnd())).isGreaterThan(math.caretX(math.sourceStart()));
    }

    private static List<BlockSpan> blocks(String markdown) {
        List<BlockSpan> result = new ArrayList<>();
        int start = 0;
        add(result, markdown, start, "---\ntitle: Demo\n---".length(), BlockKind.FRONT_MATTER);
        start = markdown.indexOf("> [!note]");
        add(result, markdown, start, markdown.indexOf("\n\n", start) - start, BlockKind.CALLOUT);
        start = markdown.indexOf("| A |");
        add(result, markdown, start, markdown.indexOf("\n\n", start) - start, BlockKind.TABLE);
        start = markdown.indexOf("$$");
        add(result, markdown, start, markdown.indexOf("\n\n", start) - start, BlockKind.MATH_BLOCK);
        start = markdown.indexOf("Inline");
        add(result, markdown, start, markdown.indexOf("\n\n", start) - start, BlockKind.PARAGRAPH);
        start = markdown.indexOf("Active");
        add(result, markdown, start, markdown.length() - start, BlockKind.PARAGRAPH);
        return result;
    }

    private static void add(List<BlockSpan> blocks, String markdown, int start, int length, BlockKind kind) {
        blocks.add(new BlockSpan(kind, start, start + length, 1));
    }

    private static AstIndex ast(String markdown, List<BlockSpan> blocks) {
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, markdown.length(), 0));
        spans.addAll(blocks);
        return new AstIndex(new Result(0, spans, new ParseMetrics(blocks.size(), 0, true)));
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
