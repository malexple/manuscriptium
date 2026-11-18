package ru.mcs.markdown.api;

import ru.mcs.metadata.MetadataProvider;

/** UI-описание Markdown без зависимости от core. */
public final class MarkdownMetadataProvider implements MetadataProvider {

    @Override
    public String id() {
        return "markdown";
    }

    @Override
    public String i18nBaseName() {
        return "ru.mcs.markdown.messages.messages";
    }
}
