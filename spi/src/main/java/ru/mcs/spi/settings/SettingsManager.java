package ru.mcs.spi.settings;

import java.util.List;

/** Реестр страниц настроек. Плагины получают scoped-представление с очисткой при выгрузке. */
public interface SettingsManager {

    void register(ConfigurableProvider provider);

    void unregister(String id);

    List<ConfigurableProvider> getProviders();
}
