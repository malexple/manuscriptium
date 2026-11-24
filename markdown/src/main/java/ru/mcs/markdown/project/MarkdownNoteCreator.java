package ru.mcs.markdown.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Создаёт новую Markdown-заметку без перезаписи и без выхода через symlink за пределы Project. */
final class MarkdownNoteCreator {

    private MarkdownNoteCreator() {
    }

    static Path create(Path project, String requestedPath) throws IOException {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new IllegalArgumentException("note path must not be empty");
        }
        String relative = requestedPath.trim();
        validateRelativeMarkdown(relative, "note path");
        Path root = ru.mcs.spi.fs.FilePaths.normalize(project);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("note path points outside project");
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
        Files.write(target, new byte[0], StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return target;
    }

    private static void validateRelativeMarkdown(String file, String description) {
        if (file.indexOf('\\') >= 0 || file.startsWith("/") || file.matches("^[A-Za-z]:($|/).*")) {
            throw new IllegalArgumentException(description + " must be a relative path with '/' separators");
        }
        try {
            if (Path.of(file).isAbsolute()) {
                throw new IllegalArgumentException(description + " must be relative");
            }
        } catch (java.nio.file.InvalidPathException e) {
            throw new IllegalArgumentException(description + " is invalid: " + e.getMessage(), e);
        }
        String[] parts = file.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException(description + " contains an invalid path segment");
            }
        }
        if (parts[0].equals(".manuscriptium") || parts[0].equals(".git")) {
            throw new IllegalArgumentException(description + " points outside the note hierarchy");
        }
        if (!file.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException(description + " must reference a Markdown file");
        }
    }
}
