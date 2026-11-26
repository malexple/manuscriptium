package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.format.CodeStyle;

class MarkdownCodeStyleTest {

    @Test
    void stateRoundTripsClampsAndFallsBackToDefaults() {
        MarkdownCodeStyleState state = new MarkdownCodeStyleState();
        assertThat(state.style()).isEqualTo(CodeStyle.defaults());

        state.setStyle(new CodeStyle(100, false, false, false, 2, 0));
        MarkdownCodeStyleState restored = new MarkdownCodeStyleState();
        restored.loadState(state.getState());
        assertThat(restored.style()).isEqualTo(state.style());

        restored.loadState(Map.of("hardWrap", 99999L, "maxBlankLines", -4, "keepLineBreaks", "yes"));
        assertThat(restored.style().hardWrap()).isEqualTo(1000);
        assertThat(restored.style().maxBlankLines()).isEqualTo(1);
        assertThat(restored.style().keepLineBreaks()).isTrue();
    }

    @Test
    void pageEditsAndAppliesTheStyle() throws Exception {
        MarkdownCodeStyleState state = new MarkdownCodeStyleState();
        MarkdownCodeStyleConfigurable configurable = new MarkdownCodeStyleConfigurable(state, key -> key);

        SwingUtilities.invokeAndWait(() -> {
            Container page = (Container) configurable.createComponent();
            assertThat(configurable.isModified()).isFalse();

            ((JSpinner) byName(page, "markdown.codestyle.hardWrap")).setValue(100);
            ((JCheckBox) byName(page, "markdown.codestyle.formatTables")).setSelected(false);
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();

            assertThat(state.style().hardWrap()).isEqualTo(100);
            assertThat(state.style().formatTables()).isFalse();
            assertThat(configurable.isModified()).isFalse();

            state.setStyle(CodeStyle.defaults());
            configurable.reset();
            assertThat(((JSpinner) byName(page, "markdown.codestyle.hardWrap")).getValue()).isEqualTo(0);
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
