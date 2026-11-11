package ru.mcs.core.plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import ru.mcs.spi.service.PersistentStateComponent;

/** Состояние реестра: плагины включены по умолчанию, на диске хранятся только отключённые ID. */
public final class PluginState implements PersistentStateComponent {

    private final Set<String> disabled = new TreeSet<>();

    public synchronized boolean isEnabled(String pluginId) {
        return !disabled.contains(pluginId);
    }

    /** @return true, если состояние изменилось. */
    public synchronized boolean setEnabled(String pluginId, boolean enabled) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("Plugin id must not be blank");
        }
        return enabled ? disabled.remove(pluginId) : disabled.add(pluginId);
    }

    public synchronized Set<String> disabledIds() {
        return Set.copyOf(disabled);
    }

    @Override
    public synchronized Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        if (!disabled.isEmpty()) {
            state.put("disabled", java.util.List.copyOf(disabled));
        }
        return state;
    }

    @Override
    public synchronized void loadState(Map<String, Object> state) {
        disabled.clear();
        if (state.get("disabled") instanceof Collection<?> ids) {
            ids.stream().map(String::valueOf).filter(id -> !id.isBlank()).forEach(disabled::add);
        }
    }
}
