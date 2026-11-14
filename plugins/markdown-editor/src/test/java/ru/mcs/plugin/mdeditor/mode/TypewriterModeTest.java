package ru.mcs.plugin.mdeditor.mode;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.view.EditorCanvas;

class TypewriterModeTest {

    @Test
    void centersCaretAndCanBeDisabled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                canvas.setDocument(new DocumentModelImpl("line\n".repeat(200),
                        new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false)));
                JScrollPane scrollPane = new JScrollPane(canvas);
                scrollPane.setSize(500, 120);
                scrollPane.getViewport().setSize(500, 120);
                canvas.setSize(500, canvas.getPreferredSize().height);
                canvas.setCaretOffset(canvas.document().length());
                int caretY = canvas.caretRect(canvas.caretOffset()).y;

                int contentHeight = canvas.getPreferredSize().height;
                canvas.setTypewriterMode(true);
                scrollPane.validate();
                assertCaretCentered(canvas, scrollPane, caretY);
                assertThat(canvas.getPreferredSize().height).isEqualTo(contentHeight + 120);

                canvas.setCaretSelection(0, 0);
                assertCaretCentered(canvas, scrollPane, canvas.caretRect(0).y);
                assertThat(scrollPane.getViewport().getViewPosition().y).isNotNegative();
                assertThat(canvas.hitTest(new java.awt.Point(canvas.caretRect(0).x,
                        canvas.caretRect(0).y + scrollPane.getViewport().getHeight() / 2))).isZero();

                canvas.setCaretOffset(500);
                assertCaretCentered(canvas, scrollPane, canvas.caretRect(500).y);

                canvas.setTypewriterMode(false);
                int maximum = Math.max(0, canvas.getPreferredSize().height - scrollPane.getViewport().getHeight());
                assertThat(scrollPane.getViewport().getViewPosition().y).isBetween(0, maximum);
                assertThat(canvas.getPreferredSize().height).isEqualTo(contentHeight);
            } finally {
                canvas.dispose();
            }
        });
    }

    private static void assertCaretCentered(EditorCanvas canvas, JScrollPane scrollPane, int caretY) {
        int padding = scrollPane.getViewport().getHeight() / 2;
        int expectedViewY = caretY + padding + canvas.caretRect(canvas.caretOffset()).height / 2
                - scrollPane.getViewport().getHeight() / 2;
        assertThat(scrollPane.getViewport().getViewPosition().y).isEqualTo(expectedViewY);
        int caretCenterInViewport = caretY + padding + canvas.caretRect(canvas.caretOffset()).height / 2
                - scrollPane.getViewport().getViewPosition().y;
        assertThat(caretCenterInViewport).isEqualTo(scrollPane.getViewport().getHeight() / 2);
    }
}
