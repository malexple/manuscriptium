package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.mode.EditorMode;

class MarkdownEditorStateTest {

    @Test
    void usesCanvasByDefaultAndRoundTripsMode() {
        MarkdownEditorState state = new MarkdownEditorState();
        assertThat(state.defaultMode()).isEqualTo(EditorMode.WYSIWYG);

        state.setDefaultMode(EditorMode.SOURCE);
        MarkdownEditorState restored = new MarkdownEditorState();
        restored.loadState(state.getState());

        assertThat(state.getState()).containsExactly(Map.entry("defaultMode", "SOURCE"));
        assertThat(restored.defaultMode()).isEqualTo(EditorMode.SOURCE);
    }

    @Test
    void ignoresMissingOrUnknownStoredMode() {
        MarkdownEditorState state = new MarkdownEditorState();
        state.loadState(Map.of());
        assertThat(state.defaultMode()).isEqualTo(EditorMode.WYSIWYG);

        state.loadState(Map.of("defaultMode", "BROKEN"));
        assertThat(state.defaultMode()).isEqualTo(EditorMode.WYSIWYG);
    }

    @Test
    void rejectsNullMode() {
        MarkdownEditorState state = new MarkdownEditorState();
        assertThatThrownBy(() -> state.setDefaultMode(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
