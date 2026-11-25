package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;

class ColorSchemeConfigurableTest {

    @Test
    void draftChangesPreviewImmediatelyAndReachServiceOnApply() throws Exception {
        ColorSchemeServiceImpl service = new ColorSchemeServiceImpl();
        service.registerGroup(ColorSchemeServiceImplTest.group());
        ColorSchemeConfigurable configurable = new ColorSchemeConfigurable(service);

        SwingUtilities.invokeAndWait(() -> {
            assertThat(configurable.createComponent()).isNotNull();
            assertThat(configurable.isModified()).isFalse();
            assertThat(configurable.previewColorAt(false, 0)).isEqualTo(new Color(0xFF0000));
            assertThat(configurable.previewColorAt(true, 0)).isEqualTo(new Color(0x00FF00));
            assertThat(configurable.previewColorAt(false, 1)).isEqualTo(new Color(ColorSchemeConfigurable.LIGHT_TEXT));
            assertThat(configurable.previewColorAt(true, 1)).isEqualTo(new Color(ColorSchemeConfigurable.DARK_TEXT));

            configurable.setColor("t", false, 0x0000FF);
            assertThat(configurable.isModified()).isTrue();
            assertThat(configurable.previewColorAt(false, 0)).isEqualTo(new Color(0x0000FF));
            assertThat(service.color("t", false)).isEqualTo(new Color(0xFF0000));

            configurable.apply();
            assertThat(service.color("t", false)).isEqualTo(new Color(0x0000FF));
            assertThat(configurable.isModified()).isFalse();
        });
    }

    @Test
    void settingDefaultColorIsNotAChangeAndResetsWork() throws Exception {
        ColorSchemeServiceImpl service = new ColorSchemeServiceImpl();
        service.registerGroup(ColorSchemeServiceImplTest.group());
        service.setOverrides(java.util.Map.of(ColorSchemeService.key("t", true), 0x123456));
        ColorSchemeConfigurable configurable = new ColorSchemeConfigurable(service);

        SwingUtilities.invokeAndWait(() -> {
            configurable.createComponent();
            assertThat(configurable.previewColorAt(true, 0)).isEqualTo(new Color(0x123456));
            assertThat(configurable.isModified()).isFalse();

            configurable.setColor("t", true, 0x00FF00);
            assertThat(configurable.isModified()).isTrue();
            configurable.setColor("t", true, 0x123456);
            assertThat(configurable.isModified()).isFalse();

            configurable.resetToken("t");
            assertThat(configurable.previewColorAt(true, 0)).isEqualTo(new Color(0x00FF00));
            configurable.apply();
            assertThat(service.overrides()).isEmpty();

            configurable.setColor("t", false, 0x010203);
            configurable.reset();
            assertThat(configurable.isModified()).isFalse();
            assertThat(configurable.previewColorAt(false, 0)).isEqualTo(new Color(0xFF0000));
        });
    }

    @Test
    void emptyServiceStillProducesAPage() throws Exception {
        ColorSchemeConfigurable configurable = new ColorSchemeConfigurable(new ColorSchemeServiceImpl());

        SwingUtilities.invokeAndWait(() -> {
            assertThat(configurable.createComponent()).isNotNull();
            assertThat(configurable.isModified()).isFalse();
        });
    }
}
