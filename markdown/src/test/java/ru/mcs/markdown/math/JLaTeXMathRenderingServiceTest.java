package ru.mcs.markdown.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class JLaTeXMathRenderingServiceTest {

    @Test
    void rendersInlineAndDisplayFormulasToArgbImages() throws Exception {
        JLaTeXMathRenderingService renderer = new JLaTeXMathRenderingService();
        BufferedImage inline = renderer.render("x^2+y^2=z^2", false, 20, Color.BLACK);
        BufferedImage display = renderer.render("\\frac{a}{b}", true, 28, Color.WHITE);
        assertThat(inline.getWidth()).isPositive();
        assertThat(inline.getHeight()).isPositive();
        assertThat(display.getWidth()).isPositive();
        assertThat(display.getHeight()).isPositive();
    }
}
