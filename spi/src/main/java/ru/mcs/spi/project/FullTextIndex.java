package ru.mcs.spi.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Пересобираемый полнотекстовый индекс Project. Все вызовы сериализуются владельцем индекса. */
public interface FullTextIndex extends AutoCloseable {

    void replaceAll(List<IndexedDocument> documents) throws IOException;

    void update(IndexedDocument document) throws IOException;

    /** Удаляет документ с таким относительным путём и всё вложенное в него поддерево. */
    void removePath(String relative) throws IOException;

    List<SearchHit> search(Path root, SearchQuery query) throws IOException;

    @Override
    void close() throws IOException;
}
