package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.layout.BlockBox;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.render.BlockRenderer;
import ru.mcs.plugin.mdeditor.render.DrawSink;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser.ParseMetrics;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;

class BlockRendererTest {

    @Test
    void recordsQuoteStripePreBackgroundRadiusAndThematicBreak() {
        String markdown = "> Quote\n\n---\n\n```java\nclass A {}\n```\n\nActive";
        int quoteEnd = markdown.indexOf("\n\n---");
        int hrStart = quoteEnd + 2;
        int hrEnd = hrStart + 3;
        int codeStart = hrEnd + 2;
        int activeStart = markdown.lastIndexOf("Active");
        List<BlockSpan> blocks = List.of(
                new BlockSpan(BlockKind.BLOCK_QUOTE, 0, quoteEnd, 1),
                new BlockSpan(BlockKind.THEMATIC_BREAK, hrStart, hrEnd, 1),
                new BlockSpan(BlockKind.FENCED_CODE, codeStart, activeStart - 2, 1, java.util.Map.of("info", "java")),
                new BlockSpan(BlockKind.PARAGRAPH, activeStart, markdown.length(), 1));
        DocumentModelImpl document = new DocumentModelImpl(markdown, DocumentProperties.utf8(LineEnding.LF, false));
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, markdown.length(), 0));
        spans.addAll(blocks);
        AstIndex ast = new AstIndex((ParseResult) new Result(0, spans, new ParseMetrics(4, 0, true)));
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, ast, new TestEditorStyleService());
        engine.setWidth(800);
        engine.setCaretOffset(activeStart);
        List<BlockBox> laidOut = engine.layout().blocks();
        BlockRenderer renderer = new BlockRenderer();

        RecordingDrawSink quote = new RecordingDrawSink();
        renderer.paint(quote, laidOut.get(0));
        BlockBox quoteBox = laidOut.get(0);
        assertThat(quote.commands).contains("fillRect:" + quoteBox.x() + ":" + quoteBox.y()
                + ":" + quoteBox.width() + ":" + quoteBox.height());
        assertThat(quote.commands).contains("line:" + quoteBox.x() + ":" + quoteBox.y() + ":" + quoteBox.x()
                + ":" + (quoteBox.y() + quoteBox.height()) + ":4");

        RecordingDrawSink thematicBreak = new RecordingDrawSink();
        renderer.paint(thematicBreak, laidOut.get(1));
        assertThat(thematicBreak.commands).hasSize(1);
        assertThat(thematicBreak.commands.getFirst()).startsWith("line:").endsWith(":1");

        RecordingDrawSink code = new RecordingDrawSink();
        renderer.paint(code, laidOut.get(2));
        BlockBox codeBox = laidOut.get(2);
        assertThat(code.commands).contains("roundRect:" + codeBox.x() + ":" + codeBox.y() + ":" + codeBox.width()
                + ":" + codeBox.height() + ":6");
        assertThat(code.commands).anyMatch(command -> command.equals("text:class"));
    }

    private static final class RecordingDrawSink implements DrawSink {
        private final List<String> commands = new ArrayList<>();

        @Override
        public void fillRect(int x, int y, int width, int height, Color color) {
            commands.add("fillRect:" + x + ":" + y + ":" + width + ":" + height);
        }

        @Override
        public void fillRoundRect(int x, int y, int width, int height, int radius, Color color) {
            commands.add("roundRect:" + x + ":" + y + ":" + width + ":" + height + ":" + radius);
        }

        @Override
        public void drawLine(int x1, int y1, int x2, int y2, int thickness, Color color) {
            commands.add("line:" + x1 + ":" + y1 + ":" + x2 + ":" + y2 + ":" + thickness);
        }

        @Override
        public void drawText(TextLayout layout, String text, float x, float baseline, Font font, Color color) {
            commands.add("text:" + text);
        }
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
