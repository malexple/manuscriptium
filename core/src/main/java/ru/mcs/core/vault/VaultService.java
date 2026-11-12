package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Жизненный цикл Vault: индекс Markdown, виртуальные views, watcher и последовательные фоновые операции. */
final class VaultService implements AutoCloseable {

    interface Listener {
        void snapshotChanged(VaultSnapshot snapshot);
    }

    private static final System.Logger LOG = System.getLogger(VaultService.class.getName());

    private final Path root;
    private final VaultIndex index;
    private final ViewStore viewStore;
    private final VaultFileWatcher watcher;
    private final Listener listener;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile VaultSnapshot snapshot = VaultSnapshot.empty();
    private List<ViewDefinition> views = List.of();
    private List<String> viewErrors = List.of();

    private VaultService(Path root, Listener listener) throws IOException, SQLException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Vault is not a directory: " + root);
        }
        this.root = VaultPaths.normalize(root);
        this.listener = listener;
        this.index = new VaultIndex(this.root);
        this.viewStore = new ViewStore(this.root);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mcs-vault-index");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Files.createDirectories(VaultPaths.viewsDir(this.root));
            this.watcher = new VaultFileWatcher(this.root, new VaultFileWatcher.Listener() {
                @Override
                public void changed(Path path) {
                    if (isViewChange(path)) {
                        submit(VaultService.this::reloadViews);
                    } else {
                        submit(() -> index.refresh(path));
                    }
                }

                @Override
                public void overflow() {
                    submit(VaultService.this::rebuild);
                }
            });
        } catch (IOException | RuntimeException e) {
            executor.shutdownNow();
            index.close();
            throw e;
        }
    }

    static VaultService open(Path root, Listener listener) throws IOException, SQLException {
        VaultService service = new VaultService(root, listener);
        try {
            Future<?> initial = service.executor.submit((Callable<Void>) () -> {
                service.rebuild();
                service.publish();
                return null;
            });
            await(initial, root);
            return service;
        } catch (IOException | SQLException | RuntimeException e) {
            service.close();
            throw e;
        }
    }

    private static void await(Future<?> future, Path root) throws IOException, SQLException {
        try {
            future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while indexing vault: " + root, e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IOException("Timed out while indexing vault: " + root, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof SQLException sql) {
                throw sql;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IOException("Cannot index vault: " + root, cause);
        }
    }

    Path root() {
        return root;
    }

    VaultSnapshot snapshot() {
        return snapshot;
    }

    List<VaultNote> notes() {
        return snapshot.notes();
    }

    /** Поиск стоит в той же очереди, что rebuild/refresh, и никогда не выполняется на EDT. */
    CompletableFuture<List<VaultSearchResult>> search(VaultSearchQuery query) {
        CompletableFuture<List<VaultSearchResult>> result = new CompletableFuture<>();
        if (closed.get()) {
            result.complete(List.of());
            return result;
        }
        try {
            executor.execute(() -> {
                if (closed.get()) {
                    result.complete(List.of());
                    return;
                }
                try {
                    result.complete(index.search(query));
                } catch (IOException | RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            result.complete(List.of());
        }
        return result;
    }

    private boolean isViewChange(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path viewsDir = VaultPaths.viewsDir(root);
        return normalized.equals(viewsDir) || normalized.getParent() != null
                && normalized.getParent().equals(viewsDir)
                && normalized.getFileName().toString().endsWith(".yml");
    }

    private void rebuild() throws IOException, SQLException {
        index.rebuild();
        reloadViews();
    }

    private void reloadViews() {
        try {
            ViewStore.LoadResult loaded = viewStore.load();
            views = loaded.views();
            viewErrors = loaded.errors();
        } catch (IOException | RuntimeException e) {
            views = List.of();
            viewErrors = List.of("views: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            LOG.log(System.Logger.Level.WARNING, "Cannot load vault views: " + root, e);
        }
    }

    CompletableFuture<Boolean> moveOrClone(String viewId, List<Integer> sourcePath,
                                           List<Integer> targetPath, boolean clone) {
        return moveOrClone(viewId, sourcePath, null, targetPath, clone);
    }

    /** Выполняет DnD в том же потоке, что и reload; expectedSourceFile отклоняет устаревший Swing-путь. */
    CompletableFuture<Boolean> moveOrClone(String viewId, List<Integer> sourcePath, String expectedSourceFile,
                                           List<Integer> targetPath, boolean clone) {
        List<Integer> source = List.copyOf(sourcePath);
        List<Integer> target = List.copyOf(targetPath);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (closed.get()) {
            result.complete(false);
            return result;
        }
        try {
            executor.execute(() -> {
                if (closed.get()) {
                    result.complete(false);
                    return;
                }
                try {
                    ViewDefinition view = null;
                    for (ViewDefinition candidate : views) {
                        if (candidate.id().equals(viewId)) {
                            view = candidate;
                            break;
                        }
                    }
                    if (view == null) {
                        throw new IOException("View no longer exists: " + viewId);
                    }
                    ViewEntry sourceEntry = ViewOperations.entryAt(view.entries(), source);
                    if (expectedSourceFile != null && (sourceEntry == null || sourceEntry.isNode()
                            || !expectedSourceFile.equals(sourceEntry.file()))) {
                        result.complete(false);
                        return;
                    }
                    boolean changed = ViewOperations.moveOrClone(view, source, target, clone);
                    if (changed) {
                        try {
                            viewStore.save(view);
                        } catch (IOException | RuntimeException e) {
                            reloadViews();
                            throw e;
                        }
                    }
                    publish();
                    result.complete(changed);
                } catch (IOException | SQLException | RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            result.complete(false);
        }
        return result;
    }

    /** Добавляет ссылку из Files в существующий или новый view. Возвращает id затронутого view. */
    CompletableFuture<String> addLink(String file, String viewId, String newViewName, List<Integer> targetPath) {
        List<Integer> target = List.copyOf(targetPath);
        CompletableFuture<String> result = new CompletableFuture<>();
        if (closed.get()) {
            result.complete(null);
            return result;
        }
        try {
            executor.execute(() -> {
                if (closed.get()) {
                    result.complete(null);
                    return;
                }
                try {
                    VaultPaths.validateRelativeMarkdown(file, "link.file");
                    Path note = root.resolve(file).normalize();
                    if (!note.startsWith(root) || !Files.isRegularFile(note, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        result.complete(null);
                        return;
                    }
                    if (viewId == null) {
                        createViewWithLink(file, newViewName, target, result);
                        return;
                    }
                    ViewDefinition view = findView(viewId);
                    if (view == null) {
                        throw new IOException("View no longer exists: " + viewId);
                    }
                    boolean changed = ViewOperations.addLink(view, file, target);
                    if (changed) {
                        try {
                            viewStore.save(view);
                        } catch (IOException | RuntimeException e) {
                            reloadViews();
                            throw e;
                        }
                    }
                    publish();
                    result.complete(changed ? view.id() : null);
                } catch (IOException | SQLException | RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            result.complete(null);
        }
        return result;
    }

    /** Переносит ссылку из Files внутри view, между views или в новый view. */
    CompletableFuture<String> moveLink(String file, String sourceViewId, List<Integer> sourcePath,
                                       String targetViewId, String newViewName, List<Integer> targetPath) {
        List<Integer> source = List.copyOf(sourcePath);
        List<Integer> target = List.copyOf(targetPath);
        CompletableFuture<String> result = new CompletableFuture<>();
        if (closed.get()) {
            result.complete(null);
            return result;
        }
        try {
            executor.execute(() -> {
                if (closed.get()) {
                    result.complete(null);
                    return;
                }
                try {
                    ViewDefinition sourceView = findView(sourceViewId);
                    ViewEntry sourceEntry = sourceView == null ? null
                            : ViewOperations.entryAt(sourceView.entries(), source);
                    if (sourceEntry == null || sourceEntry.isNode() || !sourceEntry.file().equals(file)) {
                        result.complete(null);
                        return;
                    }
                    if (sourceView.id().equals(targetViewId)) {
                        boolean changed = ViewOperations.moveOrClone(sourceView, source, target, false);
                        if (changed) {
                            try {
                                viewStore.save(sourceView);
                            } catch (IOException | RuntimeException e) {
                                reloadViews();
                                throw e;
                            }
                        }
                        publish();
                        result.complete(changed ? sourceView.id() : null);
                        return;
                    }

                    boolean createTarget = targetViewId == null;
                    ViewDefinition targetView = createTarget ? newViewWithLink(file, newViewName, target)
                            : findView(targetViewId);
                    if (targetView == null) {
                        if (createTarget) {
                            result.complete(null);
                            return;
                        }
                        throw new IOException("View no longer exists: " + targetViewId);
                    }
                    if (!createTarget && !ViewOperations.addLink(targetView, file, target)) {
                        result.complete(null);
                        return;
                    }
                    if (!ViewOperations.removeLink(sourceView, source, file)) {
                        reloadViews();
                        result.complete(null);
                        return;
                    }
                    try {
                        if (createTarget) {
                            viewStore.create(targetView);
                        } else {
                            viewStore.save(targetView);
                        }
                        viewStore.save(sourceView);
                    } catch (IOException | RuntimeException e) {
                        reloadViews();
                        throw e;
                    }
                    if (createTarget) {
                        registerView(targetView);
                    }
                    publish();
                    result.complete(targetView.id());
                } catch (IOException | SQLException | RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            result.complete(null);
        }
        return result;
    }

    private void createViewWithLink(String file, String newViewName, List<Integer> target,
                                    CompletableFuture<String> result) throws IOException, SQLException {
        ViewDefinition view = newViewWithLink(file, newViewName, target);
        if (view == null) {
            result.complete(null);
            return;
        }
        viewStore.create(view);
        registerView(view);
        publish();
        result.complete(view.id());
    }

    private ViewDefinition newViewWithLink(String file, String newViewName, List<Integer> target) {
        String name = newViewName == null ? "" : newViewName.trim();
        if (name.isEmpty() || !target.isEmpty()) {
            return null;
        }
        String base = ViewIds.base(name);
        String id = base;
        for (int suffix = 2; viewExists(id); suffix++) {
            id = base + "-" + suffix;
        }
        ViewDefinition view = new ViewDefinition(id, name, VaultPaths.viewsDir(root).resolve(id + ".yml"),
                new ArrayList<>());
        return ViewOperations.addLink(view, file, List.of()) ? view : null;
    }

    private void registerView(ViewDefinition view) {
        List<ViewDefinition> updated = new ArrayList<>(views);
        updated.add(view);
        updated.sort(Comparator.comparing(ViewDefinition::id));
        views = List.copyOf(updated);
    }

    private boolean viewExists(String id) {
        if (Files.exists(VaultPaths.viewsDir(root).resolve(id + ".yml"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        return findView(id) != null;
    }

    private ViewDefinition findView(String id) {
        for (ViewDefinition candidate : views) {
            if (candidate.id().equals(id)) {
                return candidate;
            }
        }
        return null;
    }

    private void submit(IndexTask task) {
        if (closed.get()) {
            return;
        }
        try {
            executor.execute(() -> {
                if (closed.get()) {
                    return;
                }
                try {
                    task.run();
                    publish();
                } catch (IOException | SQLException | RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "Vault indexing task failed", e);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Vault закрыт между проверкой и постановкой задачи.
        }
    }

    private void publish() throws SQLException {
        if (closed.get()) {
            return;
        }
        List<ViewDefinition> viewCopies = new ArrayList<>();
        for (ViewDefinition view : views) {
            viewCopies.add(new ViewDefinition(view.id(), view.name(), view.file(),
                    ViewOperations.copyEntries(view.entries())));
        }
        snapshot = new VaultSnapshot(index.notes(), viewCopies, viewErrors);
        try {
            listener.snapshotChanged(snapshot);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Vault listener failed", e);
        }
    }

    /** Дожидается уже поставленных операций в тестах watcher. */
    void awaitIdle() throws IOException, SQLException {
        CountDownLatch done = new CountDownLatch(1);
        try {
            executor.execute(done::countDown);
        } catch (RejectedExecutionException e) {
            return;
        }
        try {
            if (!done.await(30, TimeUnit.SECONDS)) {
                throw new IOException("Timed out while indexing vault: " + root);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while indexing vault: " + root, e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        watcher.close();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.log(System.Logger.Level.WARNING, "Vault indexing did not stop in time: " + root);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        index.close();
    }

    @FunctionalInterface
    private interface IndexTask {
        void run() throws IOException, SQLException;
    }
}
