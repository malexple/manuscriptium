package ru.mcs.markdown.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import ru.mcs.spi.fs.FilePaths;
import ru.mcs.spi.project.BranchStatisticsService;
import ru.mcs.spi.project.ProjectFileFactory;
import ru.mcs.spi.project.ViewDefinition;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.project.BookService;

/** Композиция Project-вкладов Markdown без зависимости от core-контроллера. */
public final class MarkdownProjectServices implements ProjectFileFactory, BookService, BranchStatisticsService {

    private final AtomicFileStore store;
    private final BranchStatistics statistics;

    public MarkdownProjectServices(AtomicFileStore store) {
        this.store = java.util.Objects.requireNonNull(store);
        statistics = new BranchStatistics(store, ForkJoinPool.commonPool());
    }

    @Override
    public String defaultPath(Path root, Path parent) {
        Path directory = parent == null ? root : parent;
        String prefix = directory.equals(root) ? "" : FilePaths.relativePath(root, directory) + "/";
        for (int i = 1; i < 1000; i++) {
            String name = i == 1 ? "untitled.md" : "untitled-" + i + ".md";
            if (!Files.exists(directory.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                return prefix + name;
            }
        }
        return prefix + "untitled.md";
    }

    @Override
    public Path create(Path root, String requestedPath) throws IOException {
        return MarkdownNoteCreator.create(root, requestedPath);
    }

    @Override
    public FileEditor createEditor(Path key, String name, Path root, ViewDefinition view, List<Integer> nodePath) {
        return new BookViewEditor(key, name, root, view, nodePath, store);
    }

    @Override
    public CompletableFuture<Statistics> physical(Path selected, List<Path> files) {
        return statistics.physical(selected, files);
    }

    @Override
    public CompletableFuture<Statistics> view(Path root, ViewDefinition view, List<Integer> nodePath) {
        return statistics.view(root, view, nodePath);
    }
}
