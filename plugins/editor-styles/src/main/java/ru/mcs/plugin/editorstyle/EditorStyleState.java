package ru.mcs.plugin.editorstyle;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Выбор темы редактора: auto следует светлому/тёмному режиму оболочки. */
final class EditorStyleState implements PersistentStateComponent {

    static final String AUTO = "auto";
    private volatile String theme = AUTO;

    String theme() {
        return theme;
    }

    void theme(String value) {
        theme = value == null || value.isBlank() ? AUTO : value.trim();
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("theme", theme);
        return result;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state.get("theme") instanceof String value) {
            theme(value);
        }
    }
}
