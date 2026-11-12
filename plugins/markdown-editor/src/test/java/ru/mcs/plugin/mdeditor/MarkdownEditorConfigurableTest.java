package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.spi.i18n.I18n;

class MarkdownEditorConfigurableTest {

    @Test
    void appliesAndResetsDefaultMode() throws Exception {
        MarkdownEditorState state = new MarkdownEditorState();
        MarkdownEditorConfigurable configurable = new MarkdownEditorConfigurable(state, I18n.passthrough());

        SwingUtilities.invokeAndWait(() -> {
            assertThat(configurable.createComponent()).isNotNull();
            assertThat(configurable.isModified()).isFalse();
            combo(configurable).setSelectedItem(EditorMode.SOURCE);
            assertThat(configurable.isModified()).isTrue();
            configurable.apply();
            assertThat(state.defaultMode()).isEqualTo(EditorMode.SOURCE);
            combo(configurable).setSelectedItem(EditorMode.WYSIWYG);
            configurable.reset();
            assertThat(combo(configurable).getSelectedItem()).isEqualTo(EditorMode.SOURCE);
        });
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<EditorMode> combo(MarkdownEditorConfigurable configurable) {
        return (JComboBox<EditorMode>) find(configurable.createComponent(), JComboBox.class);
    }

    private static <T> T find(java.awt.Container parent, Class<T> type) {
        for (java.awt.Component child : parent.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof java.awt.Container container) {
                T found = find(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
