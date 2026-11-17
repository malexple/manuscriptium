package ru.mcs.spi.project;

import java.io.IOException;
import java.nio.file.Path;

/** Создание документа конкретного файлового формата внутри текущего Project. */
public interface ProjectFileFactory {

    String defaultPath(Path root, Path parent);

    /** Реализация обязана отклонить выход за root, symlink-родителей и перезапись существующего файла. */
    Path create(Path root, String requestedPath) throws IOException;
}
