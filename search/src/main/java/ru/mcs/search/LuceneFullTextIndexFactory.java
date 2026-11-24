package ru.mcs.search;

import java.io.IOException;
import java.nio.file.Path;
import ru.mcs.spi.project.FullTextIndex;
import ru.mcs.spi.project.FullTextIndexFactory;

/** Создаёт Lucene-индекс в каталоге, который выбирает платформа. */
final class LuceneFullTextIndexFactory implements FullTextIndexFactory {

    @Override
    public FullTextIndex open(Path directory) throws IOException {
        return new LuceneFullTextIndex(directory);
    }
}
