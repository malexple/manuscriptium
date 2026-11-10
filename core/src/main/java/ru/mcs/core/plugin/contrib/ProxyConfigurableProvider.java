package ru.mcs.core.plugin.contrib;

import ru.mcs.core.plugin.PluginActivator;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurableProvider;

/** Метаданные страницы доступны сразу; плагин активируется только при открытии страницы. */
final class ProxyConfigurableProvider implements ConfigurableProvider {

    private final String pluginId;
    private final String id;
    private final String displayName;
    private final String parentId;
    private final PluginActivator activator;

    ProxyConfigurableProvider(String pluginId, String id, String displayName, String parentId,
                              PluginActivator activator) {
        this.pluginId = pluginId;
        this.id = id;
        this.displayName = displayName;
        this.parentId = parentId;
        this.activator = activator;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getParentId() {
        return parentId;
    }

    @Override
    public Configurable createConfigurable() {
        try {
            Configurable result = activator.activate(pluginId).createConfigurable(id);
            if (result == null) {
                throw new IllegalStateException("Plugin " + pluginId + " did not create settings page " + id);
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot activate plugin " + pluginId, e);
        }
    }
}
