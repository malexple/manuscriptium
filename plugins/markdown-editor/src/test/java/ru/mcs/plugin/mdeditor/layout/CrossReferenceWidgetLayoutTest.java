package ru.mcs.plugin.mdeditor.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.TestEditorStyleService;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;
import ru.mcs.editor.draw.DrawSink;
import ru.mcs.editor.draw.BlockWidget;
import ru.mcs.plugin.mdeditor.widget.ImageWidget;
import ru.mcs.plugin.mdeditor.widget.TableWidget;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.plugin.mdparser.FlexmarkTestAst;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;

class CrossReferenceWidgetLayoutTest {

    @Test
    void realFlexmarkAstProducesCaptionsAndKeepsTableAnchorOutsideGrid() {
        String markdown = "# Intro {#sec:intro}\n\n"
                + "![Architecture](missing.png){#fig:architecture}\n\n"
                + "| A | B |\n|---|---|\n| 1 | 2 |\n{#tbl:data}\n\n"
                + "See #sec:intro, #fig:architecture and #tbl:data.";
        DocumentModelImpl document = new DocumentModelImpl(markdown,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
        AstIndex ast = FlexmarkTestAst.parse(markdown);
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, ast, new TestEditorStyleService());
        engine.setWidgets(new WidgetRegistry(Runnable::run,
                (latex, display, size, color) -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)), null);
        engine.setWidth(800);
        engine.setCaretOffset(0);

        LayoutResult layout = engine.layout();
        BlockWidget image = layout.blocks().stream().map(BlockBox::widget)
                .filter(ImageWidget.class::isInstance).findFirst().orElseThrow();
        RecordingSink imageCommands = new RecordingSink();
        image.paint(imageCommands, 0, 0, 400);
        assertThat(imageCommands.text).anySatisfy(text -> assertThat(text).isEqualTo("Figure 1. Architecture"));

        TableWidget table = layout.blocks().stream().map(BlockBox::widget)
                .filter(TableWidget.class::isInstance).map(TableWidget.class::cast).findFirst().orElseThrow();
        assertThat(table.table().rowCount()).isEqualTo(2);
        assertThat(table.height(400)).isGreaterThan(2 * 30);
        RecordingSink tableCommands = new RecordingSink();
        table.paint(tableCommands, 0, 0, 400);
        assertThat(tableCommands.text).contains("Table 1");
        assertThat(tableCommands.text).doesNotContain("{#tbl:data}");

        String references = visibleText(layout.blocks().stream()
                .filter(block -> block.span().kind() == BlockKind.PARAGRAPH)
                .reduce((first, second) -> second).orElseThrow());
        assertThat(references).isEqualTo("See Section 1, Figure 1 and Table 1.");
        assertThat(document.text().toString()).isEqualTo(markdown);
    }

    private static String visibleText(BlockBox block) {
        StringBuilder result = new StringBuilder();
        block.lines().forEach(line -> line.runs().forEach(run -> result.append(run.text())));
        return result.toString();
    }

    private static final class RecordingSink implements DrawSink {
        private final List<String> text = new ArrayList<>();

        @Override
        public void fillRect(int x, int y, int width, int height, Color color) {
        }

        @Override
        public void fillRoundRect(int x, int y, int width, int height, int radius, Color color) {
        }

        @Override
        public void drawLine(int x1, int y1, int x2, int y2, int thickness, Color color) {
        }

        @Override
        public void drawText(TextLayout layout, String value, float x, float baseline, Font font, Color color) {
            text.add(value);
        }
    }
}
