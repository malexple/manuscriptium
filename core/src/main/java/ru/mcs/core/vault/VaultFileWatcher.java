package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Рекурсивный WatchService для Markdown и отдельное наблюдение за {@code .manuscriptium/views}. */
final class VaultFileWatcher implements AutoCloseable {

    interface Listener {
        void changed(Path path);

        void overflow();
    }

    private static final System.Logger LOG = System.getLogger(VaultFileWatcher.class.getName());

    private final Path root;
    private final WatchService service;
    private final Listener listener;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    VaultFileWatcher(Path root, Listener listener) throws IOException {
        this.root = VaultPaths.normalize(root);
        this.listener = listener;
        this.service = root.getFileSystem().newWatchService();
        try {
            registerTree(this.root);
            // Остальные служебные данные не индексируются, но создание views внутри metadata должно быть видно.
            Path metadata = VaultPaths.metadataDir(this.root);
            if (Files.isDirectory(metadata, LinkOption.NOFOLLOW_LINKS)) {
                register(metadata);
            }
            Path views = VaultPaths.viewsDir(this.root);
            if (Files.isDirectory(views, LinkOption.NOFOLLOW_LINKS)) {
                register(views);
            }
        } catch (IOException | RuntimeException e) {
            service.close();
            throw e;
        }
        thread = new Thread(this::run, "mcs-vault-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void registerTree(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (VaultPaths.isExcluded(root, dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                register(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOG.log(System.Logger.Level.WARNING, "Cannot watch directory: " + file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path directory) throws IOException {
        WatchKey key = directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        directories.put(key, directory);
    }

    private void run() {
        while (running.get()) {
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException e) {
                thread.interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }
            Path directory;
            synchronized (directories) {
                directory = directories.get(key);
            }
            if (directory == null) {
                key.reset();
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    listener.overflow();
                    continue;
                }
                if (!(event.context() instanceof Path name)) {
                    continue;
                }
                Path changed = directory.resolve(name).normalize();
                boolean viewsDirectory = changed.equals(VaultPaths.viewsDir(root));
                boolean viewFile = changed.getParent() != null
                        && changed.getParent().equals(VaultPaths.viewsDir(root));
                if (VaultPaths.isExcluded(root, changed) && !viewsDirectory && !viewFile) {
                    continue;
                }
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isDirectory(changed, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        if (viewsDirectory) {
                            register(changed);
                        } else {
                            registerTree(changed);
                        }
                    } catch (IOException e) {
                        LOG.log(System.Logger.Level.WARNING, "Cannot watch new directory: " + changed, e);
                    }
                }
                listener.changed(changed);
            }
            if (!key.reset()) {
                synchronized (directories) {
                    directories.remove(key);
                }
            }
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            service.close();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot close vault watcher", e);
        }
        thread.interrupt();
    }
}
