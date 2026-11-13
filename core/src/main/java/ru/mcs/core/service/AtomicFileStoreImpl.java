package ru.mcs.core.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import ru.mcs.spi.service.AtomicFileStore;

/** Общая JDK-only реализация атомарного хранилища файлов приложения. */
public final class AtomicFileStoreImpl implements AtomicFileStore {

    @Override
    public byte[] read(Path file) throws IOException {
        return Files.readAllBytes(resolveRegularFile(file));
    }

    @Override
    public void write(Path file, byte[] content) throws IOException {
        Objects.requireNonNull(content, "content");
        Path target = resolveRegularFile(file);
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("File has no parent directory: " + target);
        }
        Path temp = Files.createTempFile(parent, "." + target.getFileName() + ".", ".mcs-tmp");
        boolean moved = false;
        try {
            Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING);
            // Путь не должен начать указывать на другой объект, пока содержимое записывалось.
            if (!resolveRegularFile(file).equals(target)) {
                throw new IOException("File target changed while saving: " + file);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic move is not supported for " + target, e);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static Path resolveRegularFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path target = file.toAbsolutePath().normalize().toRealPath();
        BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Not a regular file: " + target);
        }
        return target;
    }
}
