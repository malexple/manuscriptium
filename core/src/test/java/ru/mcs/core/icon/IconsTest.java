package ru.mcs.core.icon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class IconsTest {

    private static final Set<String> LUCIDE = Set.of(
            "bell", "circle-question-mark", "folder-open", "info", "list-checks", "log-out",
            "message-circle", "mouse-pointer-click", "panel-bottom", "panel-left", "panel-right",
            "pin", "pin-off", "save", "save-all", "scroll-text", "search", "settings", "x");

    @Test
    void bundledLucideSetIsLoadable() {
        for (String name : LUCIDE) {
            FlatSVGIcon icon = Icons.lucide(name);
            assertThat(icon.hasFound()).as(name).isTrue();
            assertThat(icon.getIconWidth()).as(name).isEqualTo(16);
            assertThat(icon.getIconHeight()).as(name).isEqualTo(16);
        }
    }

    @Test
    void supportsExplicitSizeAndRejectsMissingResources() {
        FlatSVGIcon icon = Icons.load(Icons.class, "/ru/mcs/core/icons/lucide/save.svg", 24, 20);
        assertThat(icon.getIconWidth()).isEqualTo(24);
        assertThat(icon.getIconHeight()).isEqualTo(20);
        assertThatThrownBy(() -> Icons.load(Icons.class, "/missing.svg"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> Icons.lucide("../secret"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid");
    }

    @Test
    void repaintsCurrentColorWithComponentForeground() {
        FlatSVGIcon icon = Icons.lucide("folder-open");
        assertThat(paints(icon, new Color(0x12, 0x34, 0x56), new Color(0x12, 0x34, 0x56))).isTrue();
        assertThat(paints(icon, new Color(0xf0, 0xe0, 0xd0), new Color(0xf0, 0xe0, 0xd0))).isTrue();
    }

    @Test
    void preservesExplicitSvgColors() {
        FlatSVGIcon icon = Icons.load(Icons.class, "/ru/mcs/core/icon/accent.svg");
        Color foreground = new Color(0x11, 0x22, 0x33);
        BufferedImage image = paint(icon, foreground);
        assertThat(contains(image, foreground)).isTrue();
        assertThat(contains(image, new Color(0x35, 0x74, 0xf0))).isTrue();
    }

    private static boolean paints(FlatSVGIcon icon, Color foreground, Color expected) {
        return contains(paint(icon, foreground), expected);
    }

    private static BufferedImage paint(FlatSVGIcon icon, Color foreground) {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        JLabel component = new JLabel();
        component.setForeground(foreground);
        component.setSize(24, 24);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(component, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static boolean contains(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) != 0 && close(channel(pixel, 16), color.getRed())
                        && close(channel(pixel, 8), color.getGreen()) && close(channel(pixel, 0), color.getBlue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xff;
    }

    private static boolean close(int actual, int expected) {
        return Math.abs(actual - expected) <= 1;
    }
}
