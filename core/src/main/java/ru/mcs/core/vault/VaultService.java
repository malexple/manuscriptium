package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Жизненный цикл одного Vault: начальное сканирование, watcher и последовательные обновления индекса. */
final class VaultService implements AutoCloseable {

    interface Listener {
        void notesChanged(List<VaultNote> notes);
    }

    private static final System.Logger LOG = System.getLogger(VaultService.class.getName());

    private final Path root;
    private final VaultIndex index;
    private final VaultFileWatcher watcher;
    private final Listener listener;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile List<VaultNote> notes = List.of();

    private VaultService(Path root, Listener listener) throws IOException, SQLException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Vault is not a directory: " + root);
        }
        this.root = VaultPaths.normalize(root);
        this.listener = listener;
        this.index = new VaultIndex(this.root);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mcs-vault-index");
            thread.setDaemon(true);
            return thread;
        });
        try {
            this.watcher = new VaultFileWatcher(this.root, new VaultFileWatcher.Listener() {
                @Override
                public void changed(Path path) {
                    submit(() -> index.refresh(path));
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

    List<VaultNote> notes() {
        return notes;
    }

    private void rebuild() throws IOException, SQLException {
        index.rebuild();
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
        notes = index.notes();
        try {
            listener.notesChanged(notes);
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
