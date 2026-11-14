package ru.mcs.plugin.mdeditor.mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.editor.style.ComputedStyle;

class FocusModeTest {

    @Test
    void usesDefaultAndCssConfiguredAlphaWithSafeFallback() {
        FocusMode mode = new FocusMode();

        assertThat(mode.alpha(ComputedStyle.defaults())).isEqualTo(0.35f);
        assertThat(mode.alpha(withVariables("0.5"))).isEqualTo(0.5f);
        assertThat(mode.alpha(withVariables("broken"))).isEqualTo(0.35f);
        assertThat(mode.alpha(withVariables("-2"))).isEqualTo(0.05f);
        assertThat(mode.alpha(withVariables("3"))).isEqualTo(1.0f);
    }

    @Test
    void wrapsGraphicsWithIndependentAlphaComposite() {
        FocusMode mode = new FocusMode();
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        var copy = mode.wrap(graphics, withVariables("0.4"));
        try {
            assertThat(copy).isNotSameAs(graphics);
            assertThat(copy.getComposite()).isInstanceOf(AlphaComposite.class);
            assertThat(((AlphaComposite) copy.getComposite()).getAlpha()).isCloseTo(0.4f, within(0.0001f));
            assertThat(graphics.getComposite()).isNotSameAs(copy.getComposite());
        } finally {
            copy.dispose();
            graphics.dispose();
        }
    }

    private static ComputedStyle withVariables(String alpha) {
        ComputedStyle base = ComputedStyle.defaults();
        return new ComputedStyle(base.color(), base.backgroundColor(), base.fontFamilies(), base.fontSize(),
                base.fontWeight(), base.italic(), base.lineHeight(), base.textAlign(), base.textDecorations(),
                base.margin(), base.padding(), base.borders(), base.borderRadius(), base.maxWidth(), base.width(),
                base.letterSpacing(), base.listStyleType(), Map.of(FocusMode.ALPHA_VARIABLE, alpha));
    }
}
