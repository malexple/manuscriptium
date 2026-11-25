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
import ru.mcs.editor.model.Replace;
import ru.mcs.markdown.api.AstIndex;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;
import ru.mcs.markdown.api.MarkdownParser.ParseMetrics;
import ru.mcs.markdown.api.MarkdownParser.ParseResult;
import ru.mcs.markdown.widget.TableModel;

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
    void enterMovesDownAndLeavesTableFromLastRow() throws Exception {
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("A"));
            assertThat(canvas.tableEnter()).isTrue();
            assertThat(canvas.caretOffset()).isEqualTo(MARKDOWN.indexOf("one"));
            assertThat(document.text().toString()).isEqualTo(MARKDOWN);

            caret(canvas, MARKDOWN.indexOf("one"));
            assertThat(canvas.tableEnter()).isTrue();
            assertThat(document.text().toString()).isEqualTo(MARKDOWN + "\n\n");
            assertThat(canvas.caretOffset()).isEqualTo(MARKDOWN.length() + 2);
        });
    }

    @Test
    void typedPipesAreEscapedAndNewlinesFlattenedInsideCells() throws Exception {
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("one") + 1);

            assertThat(canvas.prepareInsertedText("a|b")).isEqualTo("a\\|b");
            assertThat(canvas.prepareInsertedText("x\ny")).isEqualTo("x y");
            assertThat(canvas.prepareInsertedText("plain")).isEqualTo("plain");
        });
    }

    @Test
    void tripleClickSelectsOnlyCellText() throws Exception {
        withCanvas((canvas, document) -> {
            int one = MARKDOWN.indexOf("one");
            caret(canvas, one + 1);

            assertThat(canvas.selectTableCell()).isTrue();
            assertThat(canvas.selectionRange().start()).isEqualTo(one);
            assertThat(canvas.selectionRange().end()).isEqualTo(one + 3);
        });
    }

    @Test
    void backspaceRemovesEscapedPipeAsOneCharacter() throws Exception {
        withCanvas((canvas, document) -> {
            int end = MARKDOWN.indexOf("one") + 3;
            document.apply(Replace.of(end, end, "\\|"));
            refresh(canvas, document);
            caret(canvas, end + 2);

            assertThat(canvas.tableBoundary(-1)).isTrue();
            assertThat(document.text().toString()).isEqualTo(MARKDOWN);
            assertThat(canvas.caretOffset()).isEqualTo(end);
        });
    }

    @Test
    void toolbarOperationsEditOnlyTheTable() throws Exception {
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("one") + 1);
            assertThat(TableController.addRowBefore(canvas)).isTrue();
            String[] lines = document.text().toString().split("\n");

            assertThat(lines).hasSize(4);
            assertThat(lines[2]).doesNotContain("one");
            assertThat(lines[3]).contains("one");
        });
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("one") + 1);
            assertThat(TableController.addColumnBefore(canvas)).isTrue();
            TableModel model = TableModel.parse(document.text().toString());

            assertThat(model.columnCount()).isEqualTo(3);
            assertThat(model.cell(1, 1)).isEqualTo("one");
        });
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("one") + 1);
            assertThat(TableController.resize(canvas, 3, 4)).isTrue();
            TableModel model = TableModel.parse(document.text().toString());

            assertThat(model.columnCount()).isEqualTo(3);
            assertThat(model.rowCount()).isEqualTo(4);
        });
    }

    @Test
    void formatAndDeleteTable() throws Exception {
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("one") + 1);
            assertThat(TableController.format(canvas)).isTrue();
            String[] lines = document.text().toString().split("\n");

            assertThat(lines).hasSize(3);
            assertThat(lines[1]).hasSameSizeAs(lines[0]);
            assertThat(lines[2]).hasSameSizeAs(lines[0]);
        });
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("two"));
            assertThat(TableController.deleteTable(canvas)).isTrue();

            assertThat(document.text().toString()).isEmpty();
            assertThat(canvas.caretOffset()).isZero();
        });
    }

    @Test
    void pastedTabularTextBecomesTableOnlyOutsideTables() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                DocumentModelImpl document = new DocumentModelImpl("Intro",
                        new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
                canvas.setDocument(document);
                canvas.setCaretSelection(5, 5);

                assertThat(canvas.pasteStructured("plain text")).isFalse();
                assertThat(canvas.pasteStructured("a\tb\n1\t2")).isTrue();
                assertThat(document.text().toString())
                        .isEqualTo("Intro\n\n| a | b |\n| ---: | ---: |\n| 1 | 2 |\n\n");
                assertThat(canvas.caretOffset()).isEqualTo(document.length());
            } finally {
                canvas.dispose();
            }
        });
        withCanvas((canvas, document) -> {
            caret(canvas, MARKDOWN.indexOf("one") + 1);

            assertThat(canvas.pasteStructured("a\tb\n1\t2")).isFalse();
            assertThat(canvas.prepareInsertedText("x\ty")).isEqualTo("x y");
        });
    }

    private static void refresh(MarkdownCanvas canvas, DocumentModelImpl document) {
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, document.length(), 0));
        spans.add(new BlockSpan(BlockKind.TABLE, 0, document.length(), 1));
        canvas.setAstIndex(new AstIndex(new Result(document.version(), spans, new ParseMetrics(1, 0, true))));
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
