package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.settings.ConfigurableProvider;
import ru.mcs.spi.swing.settings.SettingsGroups;
import ru.mcs.spi.swing.settings.SettingsManager;

class MarkdownSettingsPagesTest {

    @Test
    void registersMarkdownPagesUnderCodeStyleAndSmartKeys() {
        List<ConfigurableProvider> registered = new ArrayList<>();
        SettingsManager manager = new SettingsManager() {
            @Override
            public void register(ConfigurableProvider provider) {
                registered.add(provider);
            }

            @Override
            public void unregister(String id) {
            }

            @Override
            public List<ConfigurableProvider> getProviders() {
                return registered;
            }
        };

        MarkdownSettingsPages.register(manager, key -> key);

        assertThat(registered).extracting(ConfigurableProvider::getId)
                .containsExactly(MarkdownSettingsPages.CODE_STYLE_ID, MarkdownSettingsPages.SMART_KEYS_ID);
        assertThat(registered).extracting(ConfigurableProvider::getParentId)
                .containsExactly(SettingsGroups.CODE_STYLE, SettingsGroups.SMART_KEYS);
        for (ConfigurableProvider provider : registered) {
            var configurable = provider.createConfigurable();
            assertThat(configurable.createComponent()).isNotNull();
            assertThat(configurable.isModified()).isFalse();
        }
    }
}
