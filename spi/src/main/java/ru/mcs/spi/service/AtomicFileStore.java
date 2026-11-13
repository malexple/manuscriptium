package ru.mcs.spi.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Чтение и атомарная запись существующих файлов. Запись разрешает symlink и заменяет его целевой файл,
 * оставляя саму ссылку на месте. Временный файл создаётся в каталоге целевого файла.
 */
public interface AtomicFileStore {

    byte[] read(Path file) throws IOException;

    void write(Path file, byte[] content) throws IOException;
}
