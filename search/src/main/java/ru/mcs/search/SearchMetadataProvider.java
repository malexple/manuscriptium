package ru.mcs.search;

import ru.mcs.metadata.MetadataProvider;

/** UI-описание модуля поиска без зависимости от core. */
public final class SearchMetadataProvider implements MetadataProvider {

    @Override
    public String id() {
        return "search";
    }

    @Override
    public String i18nBaseName() {
        return "ru.mcs.search.messages.messages";
    }
}
