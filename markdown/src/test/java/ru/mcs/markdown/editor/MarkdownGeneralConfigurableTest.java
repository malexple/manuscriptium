package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MarkdownGeneralConfigurableTest {

    @Test
    void appliesLineNumberChoiceAndNotifiesOpenEditors() throws Exception {
        MarkdownSourceViewState state = new MarkdownSourceViewState();
        List<Boolean> notified = new ArrayList<>();
        MarkdownGeneralConfigurable configurable = new MarkdownGeneralConfigurable(state, notified::add, key -> key);

        SwingUtilities.invokeAndWait(() -> {
            assertThat(configurable.createComponent()).isNotNull();
            JCheckBox box = checkBox(configurable);
            assertThat(box.isSelected()).isTrue();
            assertThat(configurable.isModified()).isFalse();

            box.setSelected(false);
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();

            assertThat(state.showLineNumbers()).isFalse();
            assertThat(notified).containsExactly(false);
            box.setSelected(true);
            configurable.reset();
            assertThat(box.isSelected()).isFalse();
        });
    }

    @Test
    void stateRoundTripsAndIgnoresBrokenValues() {
        MarkdownSourceViewState state = new MarkdownSourceViewState();
        state.setShowLineNumbers(false);
        MarkdownSourceViewState restored = new MarkdownSourceViewState();
        restored.loadState(state.getState());
        assertThat(restored.showLineNumbers()).isFalse();

        MarkdownSourceViewState defaults = new MarkdownSourceViewState();
        defaults.loadState(Map.of("showLineNumbers", "yes"));
        assertThat(defaults.showLineNumbers()).isTrue();
        defaults.loadState(Map.of());
        assertThat(defaults.showLineNumbers()).isTrue();
    }

    private static JCheckBox checkBox(MarkdownGeneralConfigurable configurable) {
        return find(configurable.createComponent());
    }

    private static JCheckBox find(java.awt.Container parent) {
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof JCheckBox box) {
                return box;
            }
            if (child instanceof java.awt.Container container) {
                JCheckBox found = find(container);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
