package ru.mcs.spi.project;

import java.util.LinkedHashMap;
import java.util.Map;

/** Универсальный снимок документа для SQLite/Lucene; языковые модули добавляют metadata и attributes. */
public record IndexedDocument(String path, String title, String metadata, String content, String searchContent,
                              long size, long modifiedMs, Map<String, String> attributes) {

    public IndexedDocument {
        path = java.util.Objects.requireNonNull(path);
        title = java.util.Objects.requireNonNull(title);
        metadata = metadata == null ? "" : metadata;
        content = content == null ? "" : content;
        searchContent = searchContent == null ? content : searchContent;
        attributes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public static IndexedDocument plain(String path, String title, String content,
                                        long size, long modifiedMs) {
        return new IndexedDocument(path, title, "", content, content, size, modifiedMs, Map.of());
    }
}
