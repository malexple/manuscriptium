package ru.mcs.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class YamlSettingsTest {

    @Test
    void generalSettingsRoundTripAndIgnoreForeignTypes() {
        YamlSettings settings = new YamlSettings();
        assertThat(settings.lineNumbers() && settings.highlighting() && settings.validate() && settings.strictKeys())
                .isTrue();

        settings.set(false, true, false, true);
        YamlSettings restored = new YamlSettings();
        restored.loadState(settings.getState());
        assertThat(restored.lineNumbers()).isFalse();
        assertThat(restored.validate()).isFalse();
        restored.loadState(Map.of("lineNumbers", "yes"));
        assertThat(restored.lineNumbers()).isFalse();
    }

    @Test
    void codeStyleRoundTripsAndClamps() {
        YamlCodeStyleState state = new YamlCodeStyleState();
        assertThat(state.style()).isEqualTo(YamlStyle.defaults());

        state.setStyle(new YamlStyle(4, false, false, false, 0));
        YamlCodeStyleState restored = new YamlCodeStyleState();
        restored.loadState(state.getState());
        assertThat(restored.style()).isEqualTo(state.style());

        restored.loadState(Map.of("indentSize", 99, "maxBlankLines", -3));
        assertThat(restored.style().indentSize()).isEqualTo(8);
        assertThat(restored.style().maxBlankLines()).isZero();
    }

    @Test
    void pagesEditApplyAndNotify() throws Exception {
        YamlSettings settings = new YamlSettings();
        YamlCodeStyleState style = new YamlCodeStyleState();
        AtomicInteger notified = new AtomicInteger();
        YamlGeneralConfigurable general = new YamlGeneralConfigurable(settings, notified::incrementAndGet, key -> key);
        YamlCodeStyleConfigurable codeStyle = new YamlCodeStyleConfigurable(style, key -> key);

        SwingUtilities.invokeAndWait(() -> {
            Container generalPage = (Container) general.createComponent();
            assertThat(general.isModified()).isFalse();
            ((JCheckBox) byName(generalPage, "yaml.settings.validate")).setSelected(false);
            assertThat(general.isModified()).isTrue();
            general.apply();
            assertThat(settings.validate()).isFalse();
            assertThat(notified).hasValue(1);

            Container stylePage = (Container) codeStyle.createComponent();
            assertThat(codeStyle.isModified()).isFalse();
            ((JSpinner) byName(stylePage, "yaml.codestyle.indent")).setValue(4);
            ((JCheckBox) byName(stylePage, "yaml.codestyle.trim")).setSelected(false);
            assertThat(codeStyle.isModified()).isTrue();
            codeStyle.apply();
            assertThat(style.style().indentSize()).isEqualTo(4);
            assertThat(style.style().trimTrailingWhitespace()).isFalse();

            style.setStyle(YamlStyle.defaults());
            codeStyle.reset();
            assertThat(((JSpinner) byName(stylePage, "yaml.codestyle.indent")).getValue()).isEqualTo(2);
        });
    }

    private static Component byName(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                Component found = byName(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
