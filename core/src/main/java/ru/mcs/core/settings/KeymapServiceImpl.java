package ru.mcs.core.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.spi.service.PersistentStateComponent;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;
import ru.mcs.spi.swing.settings.Configurable;

/** Реестр областей сочетаний; переопределения пользователя сохраняются между запусками. */
public final class KeymapServiceImpl implements KeymapService, PersistentStateComponent {

    private final Map<String, List<ShortcutScope>> byLanguage = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, String> defaults = Map.of();
    private volatile Map<String, String> overrides = Map.of();

    @Override
    public void registerScope(String languageId, ShortcutScope scope) {
        synchronized (this) {
            List<ShortcutScope> scopes = byLanguage.computeIfAbsent(languageId, id -> new ArrayList<>());
            scopes.removeIf(existing -> existing.id().equals(scope.id()));
            scopes.add(scope);
            rebuildDefaults();
        }
        fire();
    }

    @Override
    public void unregisterScopes(String languageId) {
        boolean removed;
        synchronized (this) {
            removed = byLanguage.remove(languageId) != null;
            if (removed) {
                rebuildDefaults();
            }
        }
        if (removed) {
            fire();
        }
    }

    @Override
    public synchronized List<ShortcutScope> scopes(String languageId) {
        return List.copyOf(byLanguage.getOrDefault(languageId, List.of()));
    }

    @Override
    public String shortcut(String scopeId, String actionId) {
        String key = KeymapService.key(scopeId, actionId);
        String override = overrides.get(key);
        return override != null ? override : defaults.getOrDefault(key, "");
    }

    @Override
    public Map<String, String> overrides() {
        return overrides;
    }

    @Override
    public void setOverrides(Map<String, String> next) {
        Map<String, String> clean = sanitize(next);
        if (clean.equals(overrides)) {
            return;
        }
        overrides = clean;
        fire();
    }

    @Override
    public Configurable createSettings(String languageId) {
        return new KeymapConfigurable(this, languageId);
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
            Map<String, String> loaded = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : stored.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                    loaded.put(key, value);
                }
            }
            overrides = sanitize(loaded);
        }
    }

    private void rebuildDefaults() {
        Map<String, String> map = new LinkedHashMap<>();
        for (List<ShortcutScope> scopes : byLanguage.values()) {
            for (ShortcutScope scope : scopes) {
                for (ShortcutBinding binding : scope.bindings()) {
                    map.put(KeymapService.key(scope.id(), binding.actionId()), binding.defaultShortcut());
                }
            }
        }
        defaults = Map.copyOf(map);
    }

    private static Map<String, String> sanitize(Map<String, String> source) {
        Map<String, String> clean = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getKey().contains("|") && entry.getValue() != null) {
                clean.put(entry.getKey(), entry.getValue().trim());
            }
        }
        return Map.copyOf(clean);
    }

    private void fire() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                // Один неисправный слушатель не должен мешать остальным получить новые сочетания.
            }
        }
    }
}
