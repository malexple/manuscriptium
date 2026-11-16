package ru.mcs.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;

class AutoPairTest {

    @Test
    void pairsSkipsClosingAndDeletesEmptyPair() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = canvas("");
            try {
                canvas.input().insertText("(");
                assertThat(canvas.document().text().toString()).isEqualTo("()");
                assertThat(canvas.caretOffset()).isEqualTo(1);

                canvas.input().insertText(")");
                assertThat(canvas.document().text().toString()).isEqualTo("()");
                assertThat(canvas.caretOffset()).isEqualTo(2);

                canvas.setCaretOffset(1);
                canvas.input().deleteBackward();
                assertThat(canvas.document().text().toString()).isEmpty();
                assertThat(canvas.caretOffset()).isZero();
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void wrapsSelectionAndHonorsDisabledState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = canvas("body");
            try {
                canvas.setCaretSelection(0, 4);
                canvas.input().insertText("*");
                assertThat(canvas.document().text().toString()).isEqualTo("*body*");
                assertThat(canvas.selectionRange().start()).isEqualTo(1);
                assertThat(canvas.selectionRange().end()).isEqualTo(5);

                canvas.input().selectAll();
                canvas.input().insertText("x");
                canvas.setAutoPairEnabled(false);
                canvas.setCaretOffset(canvas.document().length());
                canvas.input().insertText("(");
                assertThat(canvas.document().text().toString()).isEqualTo("x(");
            } finally {
                canvas.dispose();
            }
        });
    }

    private static EditorCanvas canvas(String text) {
        EditorCanvas canvas = new EditorCanvas(new ClipboardService(new java.awt.datatransfer.Clipboard("auto-pair")));
        canvas.setSize(400, 200);
        canvas.setDocument(new DocumentModelImpl(text,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false)));
        return canvas;
    }
}
