package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObsidianImagePreviewTest {

    @TempDir
    Path tmp;

    @Test
    void scalesLargeRasterPreviewWithoutChangingSource() throws Exception {
        BufferedImage image = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        Path file = tmp.resolve("large.png");
        ImageIO.write(image, "png", file.toFile());
        byte[] original = Files.readAllBytes(file);

        Icon icon = ObsidianImagePreview.load(file);

        assertThat(icon.getIconWidth()).isEqualTo(960);
        assertThat(icon.getIconHeight()).isEqualTo(480);
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
    }

    @Test
    void loadsAndScalesSvgWithoutRecoloringIt() throws Exception {
        Path file = Files.writeString(tmp.resolve("vector.svg"), """
                <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="600">
                  <rect width="1200" height="600" fill="#cc3388"/>
                </svg>
                """);

        Icon icon = ObsidianImagePreview.load(file);

        assertThat(icon.getIconWidth()).isEqualTo(960);
        assertThat(icon.getIconHeight()).isEqualTo(480);
    }
}
