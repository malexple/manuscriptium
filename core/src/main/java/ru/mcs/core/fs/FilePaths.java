package ru.mcs.core.fs;

import java.nio.file.Path;

/** Платформенно-независимые операции над локальными путями. */
public final class FilePaths {

    private FilePaths() {
    }

    public static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /** Возвращает относительный путь с разделителем {@code /} независимо от ОС. */
    public static String relativePath(Path root, Path file) {
        Path normalizedRoot = normalize(root);
        Path normalized = normalize(file);
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            throw new IllegalArgumentException("File is outside root: " + file);
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
