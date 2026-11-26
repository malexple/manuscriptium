package ru.mcs.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;
import ru.mcs.editor.model.Replace;

class TextLayoutIncrementalTest {

    private static final DocumentProperties PROPERTIES =
            new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false);
    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static TextLayoutEngine fresh(DocumentModel document) {
        TextLayoutEngine engine = new TextLayoutEngine();
        engine.setFont(FONT);
        engine.setDocument(document);
        return engine;
    }

    private static List<String> layout(TextLayoutEngine engine) {
        return engine.visibleLines(new Rectangle(0, 0, 5000, 50000)).stream()
                .map(line -> line.start() + "-" + line.end()).toList();
    }

    @Test
    void incrementalEditsKeepTheOffsetsOfFollowingLinesExact() {
        DocumentModel document = new DocumentModelImpl("alpha\nbeta\ngamma\ndelta\nomega\n", PROPERTIES);
        TextLayoutEngine engine = fresh(document);
        document.addListener(change -> engine.changed(change));

        document.apply(Replace.of(2, 2, "XYZ"));
        assertThat(layout(engine)).isEqualTo(layout(fresh(document)));

        document.apply(Replace.of(0, 0, "top\n"));
        assertThat(layout(engine)).isEqualTo(layout(fresh(document)));

        document.apply(Replace.of(10, 10, "\n\n"));
        assertThat(layout(engine)).isEqualTo(layout(fresh(document)));

        int newline = document.text().toString().indexOf('\n');
        document.apply(Replace.of(newline, newline + 1, ""));
        assertThat(layout(engine)).isEqualTo(layout(fresh(document)));

        document.apply(Replace.of(3, 20, "q"));
        assertThat(layout(engine)).isEqualTo(layout(fresh(document)));
    }

    @Test
    void lineNumbersStayConsecutiveWhileTypingAndInsertingLines() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                DocumentModel document = new DocumentModelImpl("a\nb\nc\nd\ne\nf\n", PROPERTIES);
                canvas.setDocument(document);

                document.apply(Replace.of(7, 7, "typed"));
                document.apply(Replace.of(0, 0, "\n"));
                document.apply(Replace.of(3, 3, "\n"));
                document.apply(Replace.of(5, 5, "more"));

                List<Integer> numbers = canvas.visibleLineMarks(new Rectangle(0, 0, 800, 5000)).stream()
                        .map(EditorCanvas.LineMark::number).toList();
                assertThat(numbers).isEqualTo(IntStream.rangeClosed(1, canvas.lineCount()).boxed().toList());
            } finally {
                canvas.dispose();
            }
        });
    }
}
