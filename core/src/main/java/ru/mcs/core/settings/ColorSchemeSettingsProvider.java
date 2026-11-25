package ru.mcs.core.settings;

import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;
import ru.mcs.spi.swing.settings.SettingsGroups;

/** Editor → Color Scheme: цвета токенов подсветки всех зарегистрированных языков. */
public final class ColorSchemeSettingsProvider implements ConfigurableProvider {

    private final ColorSchemeService service;

    public ColorSchemeSettingsProvider(ColorSchemeService service) {
        this.service = service;
    }

    @Override
    public String getId() {
        return SettingsGroups.COLOR_SCHEME;
    }

    @Override
    public String getDisplayName() {
        return Messages.get("settings.editor.colors");
    }

    @Override
    public String getParentId() {
        return SettingsGroups.EDITOR;
    }

    @Override
    public Configurable createConfigurable() {
        return new ColorSchemeConfigurable(service);
    }
}
