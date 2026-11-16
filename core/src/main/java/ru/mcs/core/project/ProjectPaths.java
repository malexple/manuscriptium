package ru.mcs.core.project;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import ru.mcs.core.fs.FilePaths;

/** Расположение служебных данных Project и переносимые относительные пути заметок. */
public final class ProjectPaths {

    public static final String METADATA_DIR = ".manuscriptium";
    private static final String GIT_DIR = ".git";

    private ProjectPaths() {
    }

    public static Path normalize(Path root) {
        return FilePaths.normalize(root);
    }

    public static Path metadataDir(Path root) {
        return normalize(root).resolve(METADATA_DIR);
    }

    public static Path stateDir(Path root) {
        return metadataDir(root).resolve("state");
    }

    public static Path viewsDir(Path root) {
        return metadataDir(root).resolve("views");
    }

    public static Path databaseFile(Path root) {
        return metadataDir(root).resolve("index.db");
    }

    public static Path luceneDir(Path root) {
        return metadataDir(root).resolve("lucene");
    }

    public static boolean isMarkdown(Path file) {
        Path name = file.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    /** Markdown индексируется во всём Project, кроме служебного каталога и внутренностей Git. */
    public static boolean isExcluded(Path root, Path path) {
        Path normalizedRoot = normalize(root);
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            return false;
        }
        Path first = normalizedRoot.relativize(normalized).getName(0);
        String name = first.toString();
        return METADATA_DIR.equals(name) || GIT_DIR.equals(name);
    }

    /** Общая проверка ссылок views и имён новых заметок. */
    static void validateRelativeMarkdown(String file, String description) {
        if (file.indexOf('\\') >= 0 || file.startsWith("/") || file.matches("^[A-Za-z]:($|/).*")) {
            throw new IllegalArgumentException(description + " must be a relative path with '/' separators");
        }
        try {
            if (Path.of(file).isAbsolute()) {
                throw new IllegalArgumentException(description + " must be relative");
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(description + " is invalid: " + e.getMessage(), e);
        }
        String[] parts = file.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException(description + " contains an invalid path segment");
            }
        }
        if (parts[0].equals(METADATA_DIR) || parts[0].equals(".git")) {
            throw new IllegalArgumentException(description + " points outside the note hierarchy");
        }
        if (!file.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException(description + " must reference a Markdown file");
        }
    }

    /** Путь с {@code /} не зависит от ОС и используется как первичный ключ SQLite. */
    public static String relativePath(Path root, Path file) {
        return FilePaths.relativePath(root, file);
    }
}
