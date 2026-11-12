package ru.mcs.core.editor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import ru.mcs.core.vault.VaultPaths;

/** Разрешение Obsidian-ссылок относительно текущей заметки и корня Vault без выхода за его пределы. */
final class ObsidianLinkResolver {

    private ObsidianLinkResolver() {
    }

    static Optional<Path> note(Path root, Path source, List<Path> indexedNotes, String target) {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path effectiveRoot = effectiveRoot(root, normalizedSource);
        if (target.isBlank()) {
            return safeRegularFile(effectiveRoot, normalizedSource) ? Optional.of(normalizedSource) : Optional.empty();
        }
        String portable = portableTarget(target);
        if (portable == null) {
            return Optional.empty();
        }
        boolean rooted = portable.startsWith("/");
        String relative = rooted ? portable.substring(1) : portable;
        String withExtension = hasMarkdownExtension(relative) ? relative : relative + ".md";
        List<Path> candidates;
        try {
            candidates = directCandidates(effectiveRoot, normalizedSource, withExtension, rooted);
        } catch (java.nio.file.InvalidPathException e) {
            return Optional.empty();
        }
        for (Path candidate : candidates) {
            if (safeRegularFile(effectiveRoot, candidate)) {
                return Optional.of(candidate);
            }
        }
        if (root == null) {
            return Optional.empty();
        }

        String wanted = withExtension.toLowerCase(Locale.ROOT);
        boolean hasDirectory = withExtension.indexOf('/') >= 0;
        return indexedNotes.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> safeRegularFile(root, path))
                .filter(path -> matchesNote(root, path, wanted, hasDirectory))
                .min(Comparator.comparingInt((Path path) -> root.relativize(path).getNameCount())
                        .thenComparing(path -> VaultPaths.relativePath(root, path)));
    }

    static Optional<Path> attachment(Path root, Path source, String target) {
        if (target.isBlank()) {
            return Optional.empty();
        }
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path effectiveRoot = effectiveRoot(root, normalizedSource);
        String portable = portableTarget(target);
        if (portable == null) {
            return Optional.empty();
        }
        boolean rooted = portable.startsWith("/");
        String relative = rooted ? portable.substring(1) : portable;
        List<Path> candidates;
        try {
            candidates = directCandidates(effectiveRoot, normalizedSource, relative, rooted);
        } catch (java.nio.file.InvalidPathException e) {
            return Optional.empty();
        }
        for (Path candidate : candidates) {
            if (safeRegularFile(effectiveRoot, candidate)) {
                return Optional.of(candidate);
            }
        }
        if (root == null || relative.indexOf('/') >= 0) {
            return Optional.empty();
        }
        return findAttachmentByName(root, relative);
    }

    private static boolean matchesNote(Path root, Path path, String wanted, boolean hasDirectory) {
        String relative = VaultPaths.relativePath(root, path).toLowerCase(Locale.ROOT);
        if (hasDirectory) {
            return relative.equals(wanted) || relative.endsWith("/" + wanted);
        }
        Path name = path.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).equals(wanted);
    }

    private static List<Path> directCandidates(Path root, Path source, String relative, boolean rooted) {
        List<Path> result = new ArrayList<>(2);
        Path parent = source.getParent();
        if (!rooted && parent != null) {
            result.add(parent.resolve(relative).normalize());
        }
        if (root != null) {
            Path candidate = root.resolve(relative).normalize();
            if (!result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static String portableTarget(String target) {
        String value = target.trim();
        if (value.isEmpty() || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0) {
            return null;
        }
        if (value.matches("^[A-Za-z]:($|/).*")) {
            return null;
        }
        return value;
    }

    private static boolean hasMarkdownExtension(String value) {
        return value.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private static Optional<Path> findAttachmentByName(Path root, String name) {
        List<Path> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return VaultPaths.isExcluded(root, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path fileName = file.getFileName();
                    if (attrs.isRegularFile() && fileName != null
                            && fileName.toString().equalsIgnoreCase(name)
                            && !VaultPaths.isExcluded(root, file)) {
                        matches.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return Optional.empty();
        }
        return matches.stream().min(Comparator.comparingInt((Path path) -> root.relativize(path).getNameCount())
                .thenComparing(path -> VaultPaths.relativePath(root, path)));
    }

    private static Path effectiveRoot(Path root, Path source) {
        return root != null ? root.toAbsolutePath().normalize() : source.getParent();
    }

    private static boolean safeRegularFile(Path root, Path file) {
        Path normalizedRoot = root == null ? null : root.toAbsolutePath().normalize();
        Path normalized = file.toAbsolutePath().normalize();
        if (normalizedRoot == null || !normalized.startsWith(normalizedRoot)) {
            return false;
        }
        Path relative = normalizedRoot.relativize(normalized);
        Path current = normalizedRoot;
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            current = current.resolve(relative.getName(i));
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
        }
        return Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS);
    }
}
