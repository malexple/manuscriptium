package ru.mcs.core.toolwindow;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import ru.mcs.spi.service.PersistentStateComponent;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Раскладка: видимость панелей и размеры сторон. Сохраняется через StateManager (id core.layout). */
public final class LayoutStore implements PersistentStateComponent {

    private final Map<String, Boolean> visibility = new TreeMap<>();
    private final Map<ToolWindowAnchor, Integer> sizes = new EnumMap<>(ToolWindowAnchor.class);

    public synchronized Optional<Boolean> visible(String id) {
        return Optional.ofNullable(visibility.get(id));
    }

    public synchronized void setVisible(String id, boolean visible) {
        visibility.put(id, visible);
    }

    public synchronized int size(ToolWindowAnchor anchor, int fallback) {
        return sizes.getOrDefault(anchor, fallback);
    }

    public synchronized void setSize(ToolWindowAnchor anchor, int size) {
        sizes.put(anchor, size);
    }

    @Override
    public synchronized Map<String, Object> getState() {
        Map<String, Object> sizeState = new LinkedHashMap<>();
        sizes.forEach((anchor, size) -> sizeState.put(anchor.name(), size));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("visible", new LinkedHashMap<String, Object>(visibility));
        state.put("sizes", sizeState);
        return state;
    }

    @Override
    public synchronized void loadState(Map<String, Object> state) {
        visibility.clear();
        sizes.clear();
        if (state.get("visible") instanceof Map<?, ?> v) {
            for (Map.Entry<?, ?> e : v.entrySet()) {
                if (e.getKey() instanceof String id && e.getValue() instanceof Boolean b) {
                    visibility.put(id, b);
                }
            }
        }
        if (state.get("sizes") instanceof Map<?, ?> s) {
            for (Map.Entry<?, ?> e : s.entrySet()) {
                if (e.getKey() instanceof String name && e.getValue() instanceof Number n && n.intValue() > 0) {
                    try {
                        sizes.put(ToolWindowAnchor.valueOf(name), n.intValue());
                    } catch (IllegalArgumentException ignored) {
                        // неизвестная сторона из другой версии — пропускаем
                    }
                }
            }
        }
    }
}
