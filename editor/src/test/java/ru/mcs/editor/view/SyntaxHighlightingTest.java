package ru.mcs.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;

class SyntaxHighlightingTest {

    private static final TokenColors RED = (token, dark) -> token.equals("red") ? Color.RED : null;

    @Test
    void coloredSpanIsPaintedInTokenColorAndRestInForeground() throws Exception {
        int[] counts = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = render("WWWW WWWW", (SyntaxHighlighter) text -> List.of(new HighlightSpan(0, 4, "red")));
            counts[0] = count(image, true);
            counts[1] = count(image, false);
        });

        assertThat(counts[0]).as("red pixels").isGreaterThan(20);
        assertThat(counts[1]).as("foreground pixels").isGreaterThan(20);
    }

    @Test
    void withoutHighlighterOrOnFailureTextUsesForegroundOnly() throws Exception {
        int[] counts = new int[4];
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage plain = render("WWWW WWWW", null);
            BufferedImage broken = render("WWWW WWWW", (SyntaxHighlighter) text -> {
                throw new IllegalStateException("boom");
            });
            counts[0] = count(plain, true);
            counts[1] = count(plain, false);
            counts[2] = count(broken, true);
            counts[3] = count(broken, false);
        });

        assertThat(counts[0]).isZero();
        assertThat(counts[1]).isGreaterThan(20);
        assertThat(counts[2]).isZero();
        assertThat(counts[3]).isGreaterThan(20);
    }

    private static BufferedImage render(String text, SyntaxHighlighter highlighter) {
        EditorCanvas canvas = new EditorCanvas();
        try {
            canvas.setBackground(Color.WHITE);
            canvas.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 20));
            canvas.setForeground(Color.BLACK);
            canvas.setDocument(new DocumentModelImpl(text,
                    new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false)));
            canvas.setSize(240, 60);
            canvas.textLayout().setWidth(240);
            canvas.setSyntaxHighlighting(highlighter, RED);
            BufferedImage image = new BufferedImage(240, 60, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                canvas.paint(graphics);
            } finally {
                graphics.dispose();
            }
            return image;
        } finally {
            canvas.dispose();
        }
    }

    private static int count(BufferedImage image, boolean red) {
        int result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                boolean isRed = color.getAlpha() > 0 && color.getRed() - color.getGreen() > 60
                        && color.getRed() - color.getBlue() > 60;
                boolean isDark = color.getAlpha() > 0 && color.getRed() < 140 && color.getGreen() < 140
                        && color.getBlue() < 140;
                if (red ? isRed : isDark) {
                    result++;
                }
            }
        }
        return result;
    }
}
