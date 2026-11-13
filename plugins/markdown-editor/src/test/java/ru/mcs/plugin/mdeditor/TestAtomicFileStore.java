package ru.mcs.plugin.mdeditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import ru.mcs.spi.service.AtomicFileStore;

/** Локальная файловая реализация только для интеграционных тестов плагина без зависимости от core. */
final class TestAtomicFileStore implements AtomicFileStore {

    @Override
    public byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    @Override
    public void write(Path file, byte[] content) throws IOException {
        Path target = file.toRealPath();
        Path temp = Files.createTempFile(target.getParent(), ".markdown-editor-test-", ".tmp");
        try {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
