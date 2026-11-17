package ru.mcs.markdown.project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import ru.mcs.spi.project.BranchStatisticsService;
import ru.mcs.spi.project.ViewDefinition;
import ru.mcs.spi.service.AtomicFileStore;

/** Фоновая статистика Markdown-файлов физической ветки или видимой проекции виртуального view. */
final class BranchStatistics implements BranchStatisticsService {

    private final AtomicFileStore store;
    private final Executor executor;

    BranchStatistics(AtomicFileStore store, Executor executor) {
        this.store = store;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Statistics> physical(Path selected, List<Path> files) {
        Path normalized = selected.toAbsolutePath().normalize();
        List<Path> branch = files.stream()
                .map(file -> file.toAbsolutePath().normalize())
                .filter(file -> file.startsWith(normalized))
                .filter(file -> file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".md"))
                .toList();
        return CompletableFuture.supplyAsync(() -> read(branch), executor);
    }

    @Override
    public CompletableFuture<Statistics> view(Path root, ViewDefinition view, List<Integer> nodePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BookViewDocument book = BookViewDocument.load(root, view, nodePath, store);
                long words = 0;
                long characters = 0;
                for (BookViewDocument.Chapter chapter : book.chapters()) {
                    Statistics current = count(chapter.source());
                    words += current.words();
                    characters += current.characters();
                }
                return new Statistics(words, characters, book.chapters().size());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, executor);
    }

    private Statistics read(List<Path> files) {
        long words = 0;
        long characters = 0;
        for (Path file : files) {
            try {
                Statistics current = count(new String(store.read(file), StandardCharsets.UTF_8));
                words += current.words();
                characters += current.characters();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return new Statistics(words, characters, files.size());
    }

    private static Statistics count(String text) {
        long words = 0;
        boolean inWord = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            boolean wordPart = Character.isLetterOrDigit(codePoint) || codePoint == '_';
            if (wordPart && !inWord) {
                words++;
            }
            inWord = wordPart;
            offset += Character.charCount(codePoint);
        }
        return new Statistics(words, text.length(), 1);
    }
}
