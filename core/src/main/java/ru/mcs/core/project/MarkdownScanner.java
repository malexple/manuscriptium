package ru.mcs.core.project;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Сканирование без следования symlink: внешние каталоги не попадают в Project случайно. */
final class MarkdownScanner {

    private static final System.Logger LOG = System.getLogger(MarkdownScanner.class.getName());

    private MarkdownScanner() {
    }

    static List<MarkdownDocument> scan(Path root, Path subtree) throws IOException {
        Path normalizedRoot = ProjectPaths.normalize(root);
        if (!Files.isDirectory(subtree, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<MarkdownDocument> result = new ArrayList<>();
        Files.walkFileTree(subtree, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return ProjectPaths.isExcluded(normalizedRoot, dir)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && ProjectPaths.isMarkdown(file)
                        && !ProjectPaths.isExcluded(normalizedRoot, file)) {
                    try {
                        result.add(MarkdownDocument.read(normalizedRoot, file));
                    } catch (IOException | RuntimeException e) {
                        LOG.log(System.Logger.Level.WARNING, "Cannot index Markdown file: " + file, e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOG.log(System.Logger.Level.WARNING, "Cannot visit file during project scan: " + file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
}
