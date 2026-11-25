package ru.mcs.core.settings;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.spi.service.PersistentStateComponent;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;
import ru.mcs.spi.swing.editor.style.TokenDefinition;
import ru.mcs.spi.swing.editor.style.TokenGroup;

/** Реестр групп токенов и переопределений цветов; переопределения сохраняются между запусками. */
public final class ColorSchemeServiceImpl implements ColorSchemeService, PersistentStateComponent {

    private final Map<String, TokenGroup> groups = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, TokenDefinition> defaults = Map.of();
    private volatile Map<String, Integer> overrides = Map.of();

    @Override
    public void registerGroup(TokenGroup group) {
        synchronized (this) {
            groups.put(group.id(), group);
            rebuildDefaults();
        }
        fire();
    }

    @Override
    public void unregisterGroup(String id) {
        boolean removed;
        synchronized (this) {
            removed = groups.remove(id) != null;
            if (removed) {
                rebuildDefaults();
            }
        }
        if (removed) {
            fire();
        }
    }

    @Override
    public synchronized List<TokenGroup> groups() {
        return List.copyOf(groups.values());
    }

    @Override
    public Color color(String token, boolean dark) {
        Integer override = overrides.get(ColorSchemeService.key(token, dark));
        if (override != null) {
            return new Color(override);
        }
        TokenDefinition definition = defaults.get(token);
        return definition == null ? null : new Color(definition.rgb(dark));
    }

    @Override
    public Map<String, Integer> overrides() {
        return overrides;
    }

    @Override
    public void setOverrides(Map<String, Integer> next) {
        Map<String, Integer> clean = sanitize(next);
        if (clean.equals(overrides)) {
            return;
        }
        overrides = clean;
        fire();
    }

    @Override
    public void addChangeListener(Runnable listener) {
        listeners.addIfAbsent(listener);
    }

    @Override
    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("overrides", new LinkedHashMap<String, Object>(overrides));
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state.get("overrides") instanceof Map<?, ?> stored) {
            Map<String, Integer> loaded = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : stored.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof Number number) {
                    loaded.put(key, number.intValue());
                }
            }
            overrides = sanitize(loaded);
        }
    }

    private void rebuildDefaults() {
        Map<String, TokenDefinition> map = new LinkedHashMap<>();
        for (TokenGroup group : groups.values()) {
            for (TokenDefinition definition : group.tokens()) {
                map.putIfAbsent(definition.id(), definition);
            }
        }
        defaults = Map.copyOf(map);
    }

    private static Map<String, Integer> sanitize(Map<String, Integer> source) {
        Map<String, Integer> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            boolean validKey = key != null && (key.endsWith(":light") || key.endsWith(":dark"));
            if (validKey && value != null && value >= 0 && value <= 0xFFFFFF) {
                clean.put(key, value);
            }
        }
        return Map.copyOf(clean);
    }

    private void fire() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                // Один неисправный слушатель не должен мешать остальным получить новые цвета.
            }
        }
    }
}
