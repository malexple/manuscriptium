package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.view.EditorCanvas;

class EditorCanvasTest {

    @Test
    void paintsHeadlesslyAndUpdatesStatus() throws Exception {
        EditorCanvas canvas = new EditorCanvas();
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
        EditorCanvas canvas = new EditorCanvas();
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
