package ru.mcs.markdown.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;
import ru.mcs.markdown.api.AstIndex;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;
import ru.mcs.markdown.api.MarkdownParser.ParseMetrics;
import ru.mcs.markdown.api.MarkdownParser.ParseResult;
import ru.mcs.markdown.editor.TestEditorStyleService;
import ru.mcs.markdown.widget.TableWidget;
import ru.mcs.markdown.widget.WidgetRegistry;

class ActiveTableLayoutTest {

    private static final String MARKDOWN = "Intro\n\n| A | B |\n|---|---|\n| one | two |\n\nTail";

    @Test
    void activeTableStaysRenderedAndMapsCaretAndClicksIntoCells() {
        MarkdownLayoutEngine engine = engine();
        int caret = MARKDOWN.indexOf("two") + 1;
        engine.setCaretOffset(caret);

        BlockBox table = engine.layout().blocks().stream()
                .filter(block -> block.widget() instanceof TableWidget).findFirst().orElseThrow();
        Rectangle rect = engine.caretRect(caret);

        assertThat(table.source()).isFalse();
        assertThat(table.lines()).isEmpty();
        assertThat(rect.y).isGreaterThanOrEqualTo(table.contentY()).isLessThan(table.contentY() + table.height());
        assertThat(rect.x).isGreaterThan(table.contentX() + table.contentWidth() / 2);
        assertThat(engine.hitTest(new Point(rect.x, rect.y + rect.height / 2))).isEqualTo(caret);
    }

    @Test
    void clickingInactiveTableCellReturnsOffsetInsideThatCell() {
        MarkdownLayoutEngine engine = engine();
        engine.setCaretOffset(0);
        BlockBox table = engine.layout().blocks().stream()
                .filter(block -> block.widget() instanceof TableWidget).findFirst().orElseThrow();

        int offset = engine.hitTest(new Point(table.contentX() + 12, table.contentY() + 45));

        assertThat(offset).isBetween(MARKDOWN.indexOf("one"), MARKDOWN.indexOf("one") + 3);
    }

    private static MarkdownLayoutEngine engine() {
        DocumentModelImpl document = new DocumentModelImpl(MARKDOWN,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
        List<BlockSpan> blocks = new ArrayList<>();
        int table = MARKDOWN.indexOf("| A |");
        int tableEnd = MARKDOWN.indexOf("\n\nTail");
        blocks.add(new BlockSpan(BlockKind.PARAGRAPH, 0, 5, 1));
        blocks.add(new BlockSpan(BlockKind.TABLE, table, tableEnd, 1));
        blocks.add(new BlockSpan(BlockKind.PARAGRAPH, tableEnd + 2, MARKDOWN.length(), 1));
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, MARKDOWN.length(), 0));
        spans.addAll(blocks);
        AstIndex ast = new AstIndex(new Result(0, spans, new ParseMetrics(blocks.size(), 0, true)));
        WidgetRegistry widgets = new WidgetRegistry(Runnable::run,
                (latex, display, size, color) -> new BufferedImage(32, 18, BufferedImage.TYPE_INT_ARGB));
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, ast, new TestEditorStyleService());
        engine.setWidgets(widgets, null);
        engine.setWidth(800);
        return engine;
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
