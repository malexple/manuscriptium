package ru.mcs.yaml;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;

/** Страницы YAML в группах редактора; название у всех — «YAML». */
final class YamlSettingsPages {

    private YamlSettingsPages() {
    }

    static ConfigurableProvider provider(String id, String parentId, UnaryOperator<String> text,
                                         Supplier<Configurable> factory) {
        return new ConfigurableProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return text.apply("settings.yaml.language");
            }

            @Override
            public String getParentId() {
                return parentId;
            }

            @Override
            public Configurable createConfigurable() {
                return factory.get();
            }
        };
    }
}
