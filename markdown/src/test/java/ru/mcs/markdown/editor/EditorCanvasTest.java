package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.editor.view.MarkdownCanvas;

class EditorCanvasTest {

    @Test
    void paintsHeadlesslyAndUpdatesStatus() throws Exception {
        MarkdownCanvas canvas = new MarkdownCanvas();
        canvas.setSize(640, 480);
        canvas.setStatus("Ready");
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);

        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = image.createGraphics();
            try {
                canvas.paint(g);
            } finally {
                g.dispose();
            }
        });

        assertThat(canvas.getStatus()).isEqualTo("Ready");
        assertThat(canvas.isFocusable()).isTrue();
        assertThat(image.getRGB(0, 0)).isEqualTo(canvas.getBackground().getRGB());
    }

    @Test
    void acceptsNullStatusWithoutFailingPaint() {
        MarkdownCanvas canvas = new MarkdownCanvas();
        canvas.setSize(0, 0);
        canvas.setStatus(null);

        assertThat(canvas.getStatus()).isEmpty();
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            canvas.paint(g);
        } finally {
            g.dispose();
        }
    }
}
