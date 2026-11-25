package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.draw.Graphics2DDrawSink;

class DrawSinkClipTest {

    @Test
    void clipLimitsDrawingUntilUnclip() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Graphics2DDrawSink sink = new Graphics2DDrawSink(graphics);
            sink.clip(0, 0, 10, 10);
            sink.fillRect(0, 0, 20, 20, Color.RED);
            sink.unclip();
            sink.fillRect(15, 15, 2, 2, Color.BLUE);
        } finally {
            graphics.dispose();
        }

        assertThat(image.getRGB(5, 5)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(12, 12) >>> 24).isZero();
        assertThat(image.getRGB(15, 15)).isEqualTo(Color.BLUE.getRGB());
    }
}
