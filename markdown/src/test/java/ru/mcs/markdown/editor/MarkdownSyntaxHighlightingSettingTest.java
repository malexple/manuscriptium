package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MarkdownSyntaxHighlightingSettingTest {

    @Test
    void highlightingToggleIsAppliedAndReported() throws Exception {
        MarkdownSourceViewState state = new MarkdownSourceViewState();
        List<Boolean> reported = new ArrayList<>();
        MarkdownGeneralConfigurable configurable = new MarkdownGeneralConfigurable(state,
                current -> reported.add(current.showSyntaxHighlighting()), key -> key);

        SwingUtilities.invokeAndWait(() -> {
            JCheckBox box = byName(configurable.createComponent(), "markdown.settings.syntaxHighlighting");
            assertThat(box.isSelected()).isTrue();
            assertThat(configurable.isModified()).isFalse();

            box.setSelected(false);
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();

            assertThat(state.showSyntaxHighlighting()).isFalse();
            assertThat(state.showLineNumbers()).isTrue();
            assertThat(reported).containsExactly(false);
        });
    }

    @Test
    void stateKeepsBothFlagsAndDefaultsToEnabled() {
        MarkdownSourceViewState state = new MarkdownSourceViewState();
        assertThat(state.showSyntaxHighlighting()).isTrue();

        state.setShowSyntaxHighlighting(false);
        MarkdownSourceViewState restored = new MarkdownSourceViewState();
        restored.loadState(state.getState());

        assertThat(restored.showSyntaxHighlighting()).isFalse();
        assertThat(restored.showLineNumbers()).isTrue();
        restored.loadState(Map.of("showSyntaxHighlighting", "yes"));
        assertThat(restored.showSyntaxHighlighting()).isFalse();
    }

    private static JCheckBox byName(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JCheckBox box && name.equals(box.getName())) {
                return box;
            }
            if (child instanceof Container container) {
                JCheckBox found = byName(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
