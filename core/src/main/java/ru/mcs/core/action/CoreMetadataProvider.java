package ru.mcs.core.action;

import ru.mcs.metadata.MetadataProvider;

/** UI-описание core; реализации действий и состояние остаются в Java. */
public final class CoreMetadataProvider implements MetadataProvider {

    @Override
    public String id() {
        return "core";
    }

    @Override
    public String i18nBaseName() {
        return "ru.mcs.core.i18n.messages";
    }
}
