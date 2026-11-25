package ru.mcs.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;

class LineNumberGutterTest {

    private static final String TEXT = "one two three four five six seven\nsecond\n\nlast";

    @Test
    void marksNumberPhysicalLinesOnlyAtTheirFirstVisualLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                canvas.setDocument(document(TEXT));
                canvas.textLayout().setWidth(100 + 2 * TextLayoutEngine.HORIZONTAL_PADDING);

                List<EditorCanvas.LineMark> marks = canvas.visibleLineMarks(new Rectangle(0, 0, 800, 1000));

                assertThat(marks).extracting(EditorCanvas.LineMark::number).containsExactly(1, 2, 3, 4);
                assertThat(marks).extracting(EditorCanvas.LineMark::y).isSorted();
                assertThat(marks.get(1).y() - marks.get(0).y()).isGreaterThan(canvas.lineHeight());
                assertThat(canvas.lineCount()).isEqualTo(4);
                canvas.setCaretOffset(TEXT.indexOf("second"));
                assertThat(canvas.currentLineNumber()).isEqualTo(2);
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void selectingLinesCoversWholeLinesInBothDirections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                canvas.setDocument(document(TEXT));
                canvas.textLayout().setWidth(800);
                List<EditorCanvas.LineMark> marks = canvas.visibleLineMarks(new Rectangle(0, 0, 800, 1000));
                int second = TEXT.indexOf("second");
                int last = TEXT.indexOf("last");

                canvas.selectLines(marks.get(1).y() + 1, marks.get(3).y() + 1);
                assertThat(canvas.selectionRange().start()).isEqualTo(second);
                assertThat(canvas.selectionRange().end()).isEqualTo(TEXT.length());

                canvas.selectLines(marks.get(2).y() + 1, marks.get(1).y() + 1);
                assertThat(canvas.selectionRange().start()).isEqualTo(second);
                assertThat(canvas.selectionRange().end()).isEqualTo(last);
                assertThat(canvas.caretOffset()).isEqualTo(second);
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void gutterGrowsWithDigitsAndStopsListeningAfterDispose() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas small = new EditorCanvas();
            EditorCanvas large = new EditorCanvas();
            try {
                small.setDocument(document("a\nb"));
                large.setDocument(document("x\n".repeat(12_000)));
                LineNumberGutter smallGutter = new LineNumberGutter(small);
                LineNumberGutter largeGutter = new LineNumberGutter(large);

                assertThat(largeGutter.getPreferredSize().width).isGreaterThan(smallGutter.getPreferredSize().width);
                smallGutter.dispose();
                largeGutter.dispose();
            } finally {
                small.dispose();
                large.dispose();
            }
        });
    }

    private static DocumentModelImpl document(String text) {
        return new DocumentModelImpl(text,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
    }
}
