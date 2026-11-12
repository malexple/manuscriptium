package ru.mcs.core.vault;

import java.nio.file.Path;
import java.util.Locale;

/** Расположение служебных данных Vault и переносимые относительные пути заметок. */
public final class VaultPaths {

    public static final String METADATA_DIR = ".manuscriptium";
    private static final String GIT_DIR = ".git";

    private VaultPaths() {
    }

    public static Path normalize(Path root) {
        return root.toAbsolutePath().normalize();
    }

    public static Path metadataDir(Path root) {
        return normalize(root).resolve(METADATA_DIR);
    }

    public static Path stateDir(Path root) {
        return metadataDir(root).resolve("state");
    }

    public static Path databaseFile(Path root) {
        return metadataDir(root).resolve("index.db");
    }

    public static boolean isMarkdown(Path file) {
        Path name = file.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    /** Markdown индексируется во всём Vault, кроме служебного каталога и внутренностей Git. */
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

    /** Путь с {@code /} не зависит от ОС и используется как первичный ключ SQLite. */
    public static String relativePath(Path root, Path file) {
        Path normalizedRoot = normalize(root);
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            throw new IllegalArgumentException("File is outside vault: " + file);
        }
        StringBuilder result = new StringBuilder();
        for (Path part : normalizedRoot.relativize(normalized)) {
            if (!result.isEmpty()) {
                result.append('/');
            }
            result.append(part);
        }
        return result.toString();
    }
}
