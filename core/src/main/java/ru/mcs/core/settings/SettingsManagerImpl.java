package ru.mcs.core.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ru.mcs.spi.settings.ConfigurableProvider;
import ru.mcs.spi.settings.SettingsManager;

public final class SettingsManagerImpl implements SettingsManager {

    private final Map<String, ConfigurableProvider> providers = new LinkedHashMap<>();
    private final Map<String, ScopedSettingsManager> scopes = new ConcurrentHashMap<>();

    @Override
    public synchronized void register(ConfigurableProvider provider) {
        if (provider == null || isBlank(provider.getId()) || isBlank(provider.getDisplayName())) {
            throw new IllegalArgumentException("Settings provider requires non-blank id and displayName");
        }
        if (providers.containsKey(provider.getId())) {
            throw new IllegalArgumentException("Settings page already registered: " + provider.getId());
        }
        providers.put(provider.getId(), provider);
    }

    @Override
    public synchronized void unregister(String id) {
        providers.remove(id);
    }

    @Override
    public synchronized List<ConfigurableProvider> getProviders() {
        return List.copyOf(providers.values());
    }

    public SettingsManager scopedFor(String pluginId) {
        return scopes.computeIfAbsent(pluginId, ScopedSettingsManager::new);
    }

    public void unregisterAll(String pluginId) {
        ScopedSettingsManager scope = scopes.remove(pluginId);
        if (scope != null) {
            scope.cleanup();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private final class ScopedSettingsManager implements SettingsManager {

        private final String pluginId;
        private final Set<String> ownedIds = ConcurrentHashMap.newKeySet();

        private ScopedSettingsManager(String pluginId) {
            this.pluginId = pluginId;
        }

        @Override
        public void register(ConfigurableProvider provider) {
            SettingsManagerImpl.this.register(provider);
            ownedIds.add(provider.getId());
        }

        @Override
        public void unregister(String id) {
            if (ownedIds.remove(id)) {
                SettingsManagerImpl.this.unregister(id);
            }
        }

        @Override
        public List<ConfigurableProvider> getProviders() {
            return SettingsManagerImpl.this.getProviders();
        }

        private void cleanup() {
            for (String id : new ArrayList<>(ownedIds)) {
                SettingsManagerImpl.this.unregister(id);
            }
            ownedIds.clear();
        }
    }
}
