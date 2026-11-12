package ru.mcs.plugin.properties;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Безопасное чтение и атомарная замена Markdown без следования symlink. */
final class MarkdownFileStore {

    private MarkdownFileStore() {
    }

    static String read(Path file) throws IOException {
        Path regular = requireRegularFile(file);
        return Files.readString(regular);
    }

    static void writeAtomically(Path file, String text) throws IOException {
        Path target = requireRegularFile(file);
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Markdown file has no parent directory: " + target);
        }
        Path temp = Files.createTempFile(parent, "." + target.getFileName() + ".", ".mcs-tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, text, StandardOpenOption.TRUNCATE_EXISTING);
            // Повторная проверка сужает TOCTOU-окно перед заменой.
            requireRegularFile(target);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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

    private static Path requireRegularFile(Path file) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) {
            throw new IOException("Cannot determine filesystem root: " + absolute);
        }
        for (Path part : absolute) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not accepted: " + current);
            }
        }
        BasicFileAttributes attributes = Files.readAttributes(absolute, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Not a regular Markdown file: " + absolute);
        }
        return absolute;
    }
}
