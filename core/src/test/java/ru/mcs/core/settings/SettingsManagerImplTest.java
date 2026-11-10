package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurableProvider;
import ru.mcs.spi.settings.SettingsManager;

class SettingsManagerImplTest {

    @Test
    void registersProvidersAndRejectsInvalidOrDuplicatePages() {
        SettingsManagerImpl manager = new SettingsManagerImpl();
        manager.register(provider("b"));
        manager.register(provider("a"));

        assertThat(manager.getProviders()).extracting(ConfigurableProvider::getId)
                .containsExactly("b", "a");
        assertThatThrownBy(() -> manager.register(provider("a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        assertThatThrownBy(() -> manager.register(provider(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopedManagerUnregistersOnlyItsOwnPages() {
        SettingsManagerImpl manager = new SettingsManagerImpl();
        SettingsManager first = manager.scopedFor("first");
        SettingsManager second = manager.scopedFor("second");
        first.register(provider("first.page"));
        second.register(provider("second.page"));

        first.unregister("second.page");
        assertThat(manager.getProviders()).extracting(ConfigurableProvider::getId)
                .containsExactly("first.page", "second.page");

        first.unregister("first.page");
        assertThat(manager.getProviders()).extracting(ConfigurableProvider::getId)
                .containsExactly("second.page");
    }

    @Test
    void cleanupRemovesAllPagesOwnedByPlugin() {
        SettingsManagerImpl manager = new SettingsManagerImpl();
        SettingsManager scoped = manager.scopedFor("p");
        scoped.register(provider("p.one"));
        scoped.register(provider("p.two"));
        manager.register(provider("core.general"));

        manager.unregisterAll("p");

        assertThat(manager.getProviders()).extracting(ConfigurableProvider::getId)
                .containsExactly("core.general");
        assertThat(manager.scopedFor("p").getProviders()).hasSize(1);
    }

    private static ConfigurableProvider provider(String id) {
        return new ConfigurableProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return id;
            }

            @Override
            public Configurable createConfigurable() {
                return null;
            }
        };
    }
}
