package ru.mcs.markdown.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;
import ru.mcs.markdown.api.AstIndex;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;
import ru.mcs.markdown.api.MarkdownParser.ParseMetrics;
import ru.mcs.markdown.api.MarkdownParser.ParseResult;

class EditorCanvasTableEditingTest {

    private static final String MARKDOWN = "| A | B |\n|---|---|\n| one | two |";

    @Test
    void arrowsJumpBetweenCellsOnlyAtCellEdges() throws Exception {
        withCanvas((canvas, document) -> {
            int oneEnd = MARKDOWN.indexOf("one") + 3;
            int twoStart = MARKDOWN.indexOf("two");

            caret(canvas, oneEnd);
            assertThat(canvas.tableStep(1)).isTrue();
            assertThat(canvas.caretOffset()).isEqualTo(twoStart);
            assertThat(canvas.tableStep(-1)).isTrue();
            assertThat(canvas.caretOffset()).isEqualTo(oneEnd);

            caret(canvas, MARKDOWN.indexOf("one") + 1);
            assertThat(canvas.tableStep(1)).isFalse();
            caret(canvas, MARKDOWN.length());
            assertThat(canvas.tableStep(1)).isFalse();
            caret(canvas, MARKDOWN.indexOf("A"));
            assertThat(canvas.tableStep(-1)).isFalse();
            assertThat(document.text().toString()).isEqualTo(MARKDOWN);
        });
    }

    @Test
    void backspaceAndDeleteNeverReachDelimiters() throws Exception {
        withCanvas((canvas, document) -> {
            int two = MARKDOWN.indexOf("two");

            caret(canvas, two);
            assertThat(canvas.tableBoundary(-1)).isTrue();
            caret(canvas, two + 3);
            assertThat(canvas.tableBoundary(1)).isTrue();
            caret(canvas, two + 1);
            assertThat(canvas.tableBoundary(-1)).isFalse();
            assertThat(canvas.tableBoundary(1)).isFalse();
        });
    }

    @Test
    void enterMovesDownAndAppendsRowAfterLastOne() throws Exception {
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("A"));
            assertThat(canvas.tableEnter()).isTrue();
            assertThat(canvas.caretOffset()).isEqualTo(MARKDOWN.indexOf("one"));
            assertThat(document.text().toString()).isEqualTo(MARKDOWN);

            caret(canvas, MARKDOWN.indexOf("one"));
            assertThat(canvas.tableEnter()).isTrue();
            String changed = document.text().toString();
            assertThat(changed.split("\n")).hasSize(4);
            assertThat(canvas.caretOffset()).isEqualTo(changed.lastIndexOf('\n') + 1 + 2);
        });
    }

    private static void caret(MarkdownCanvas canvas, int offset) {
        canvas.setCaretSelection(offset, offset);
    }

    private static void withCanvas(Body body) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                DocumentModelImpl document = new DocumentModelImpl(MARKDOWN,
                        new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
                canvas.setDocument(document);
                List<BlockSpan> spans = new ArrayList<>();
                spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, MARKDOWN.length(), 0));
                spans.add(new BlockSpan(BlockKind.TABLE, 0, MARKDOWN.length(), 1));
                canvas.setAstIndex(new AstIndex(new Result(0, spans, new ParseMetrics(1, 0, true))));
                body.accept(canvas, document);
            } finally {
                canvas.dispose();
            }
        });
    }

    private interface Body {
        void accept(MarkdownCanvas canvas, DocumentModelImpl document);
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
