package ru.mcs.markdown.project;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import ru.mcs.spi.service.AtomicFileStore;

/** Та же JDK-семантика атомарной записи, без тестовой зависимости Markdown на core. */
final class TestAtomicFileStore implements AtomicFileStore {

    @Override
    public byte[] read(Path file) throws IOException {
        return Files.readAllBytes(resolveRegularFile(file));
    }

    @Override
    public void write(Path file, byte[] content) throws IOException {
        Path target = resolveRegularFile(file);
        Path temp = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".", ".mcs-tmp");
        boolean moved = false;
        try {
            Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING);
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
        Path target = file.toAbsolutePath().normalize().toRealPath();
        BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Not a regular file: " + target);
        }
        return target;
    }
}
