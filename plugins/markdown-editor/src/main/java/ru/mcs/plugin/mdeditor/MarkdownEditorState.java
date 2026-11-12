package ru.mcs.plugin.mdeditor;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.spi.service.PersistentStateComponent;

/** Сохраняемые настройки редактора; режим открытых вкладок не меняется при редактировании этой страницы. */
final class MarkdownEditorState implements PersistentStateComponent {

    private volatile EditorMode defaultMode = EditorMode.WYSIWYG;

    EditorMode defaultMode() {
        return defaultMode;
    }

    void setDefaultMode(EditorMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Editor mode must not be null");
        }
        defaultMode = mode;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("defaultMode", defaultMode.name());
        return Map.copyOf(state);
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (!(state.get("defaultMode") instanceof String value)) {
            return;
        }
        try {
            defaultMode = EditorMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            defaultMode = EditorMode.WYSIWYG;
        }
    }
}
