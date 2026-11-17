package ru.mcs.spi.project;

import java.io.IOException;
import java.nio.file.Path;

/** Языковой вклад в пересобираемый Project-индекс. Вызывается вне EDT после безопасного сканирования файлов. */
public interface IndexContributor {

    boolean supports(Path file);

    /**
     * @param text корректный UTF-8; invalid UTF-8 отфильтровывается сканером для поддерживаемых текстовых форматов
     */
    IndexedDocument contribute(Path root, Path file, String text, long size, long modifiedMs) throws IOException;
}
