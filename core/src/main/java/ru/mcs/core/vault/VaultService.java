package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
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
