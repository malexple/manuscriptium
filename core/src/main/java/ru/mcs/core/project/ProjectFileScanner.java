package ru.mcs.core.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ru.mcs.spi.project.IndexContributor;
import ru.mcs.spi.project.IndexContributorRegistry;
import ru.mcs.spi.project.IndexedDocument;

/** Сканирование без следования symlink. Имя индексируется всегда; языковой вклад обогащает текстовый документ. */
final class ProjectFileScanner {

    private static final System.Logger LOG = System.getLogger(ProjectFileScanner.class.getName());

    private ProjectFileScanner() {
    }

    static List<IndexedDocument> scan(Path root, Path subtree, IndexContributorRegistry registry) throws IOException {
        Path normalizedRoot = ProjectPaths.normalize(root);
        if (!Files.isDirectory(subtree, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<IndexedDocument> result = new ArrayList<>();
        Files.walkFileTree(subtree, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return ProjectPaths.isExcluded(normalizedRoot, dir)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !ProjectPaths.isExcluded(normalizedRoot, file)) {
                    read(normalizedRoot, file, registry).ifPresent(result::add);
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

    static Optional<IndexedDocument> read(Path root, Path file, IndexContributorRegistry registry) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            List<IndexContributor> contributors = registry.contributors().stream()
                    .filter(contributor -> contributor.supports(file)).toList();
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException invalidText) {
                if (!contributors.isEmpty()) {
                    throw invalidText;
                }
                // Binary file remains searchable by path, but is not decoded or sent to a language contributor.
                text = null;
            }
            if (!contributors.isEmpty()) {
                return Optional.of(contributors.getFirst().contribute(root, file, text, size, modified));
            }
            String relative = ProjectPaths.relativePath(root, file);
            String name = file.getFileName().toString();
            return Optional.of(IndexedDocument.plain(relative, name, text == null ? "" : text, size, modified));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot index project file: " + file, e);
            return Optional.empty();
        }
    }
}
