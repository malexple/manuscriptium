package ru.mcs.spi.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Read-only снимок текущего Project для языковых модулей и расширений.
 * Реализация потокобезопасна; {@link #revision()} позволяет отбросить устаревший async-результат.
 */
public interface ProjectAccess {

    Optional<Path> root();

    /** Нормализованные файлы текущего индекса, отсортированные по переносимому относительному пути. */
    List<Path> files();

    Optional<ViewDefinition> view(String id);

    /** Меняется при смене Project и после публикации нового снимка индекса/views. */
    long revision();
}
