package ru.mcs.core.settings;

import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;

/** Страница сочетаний клавиш языка или общей области редактора; дочерние страницы подключают плагины. */
public final class KeymapSettingsProvider implements ConfigurableProvider {

    private final KeymapService service;
    private final String languageId;
    private final String id;
    private final String titleKey;
    private final String parentId;

    public KeymapSettingsProvider(KeymapService service, String languageId, String id, String titleKey,
                                  String parentId) {
        this.service = service;
        this.languageId = languageId;
        this.id = id;
        this.titleKey = titleKey;
        this.parentId = parentId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return Messages.get(titleKey);
    }

    @Override
    public String getParentId() {
        return parentId;
    }

    @Override
    public Configurable createConfigurable() {
        return service.createSettings(languageId);
    }
}
