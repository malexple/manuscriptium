package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Создаёт новую Markdown-заметку без перезаписи и без выхода через symlink за пределы Vault. */
final class MarkdownNoteCreator {

    private MarkdownNoteCreator() {
    }

    static Path create(Path vault, String requestedPath) throws IOException {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new IllegalArgumentException("note path must not be empty");
        }
        String relative = requestedPath.trim();
        VaultPaths.validateRelativeMarkdown(relative, "note path");
        Path root = VaultPaths.normalize(vault);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("note path points outside vault");
        }
        Path relativePath = root.relativize(target);
        Path current = root;
        for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
            current = current.resolve(relativePath.getName(i).toString());
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Note parent is not a real directory: " + current);
                }
            } else {
                Files.createDirectory(current);
            }
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(target.toString());
        }
        String filename = target.getFileName().toString();
        String title = filename.substring(0, filename.length() - ".md".length());
        Files.writeString(target, "# " + title + "\n\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return target;
    }
}
