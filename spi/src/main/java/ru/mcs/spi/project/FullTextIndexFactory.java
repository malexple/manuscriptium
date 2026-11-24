package ru.mcs.spi.project;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Поставщик полнотекстового индекса. Регистрируется в ServiceRegistry модулем поиска;
 * без него Project работает, но поиск недоступен.
 */
public interface FullTextIndexFactory {

    FullTextIndex open(Path directory) throws IOException;
}
