package ru.mcs.core.project;

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

/** Рекурсивный WatchService для документов и отдельное наблюдение за {@code .manuscriptium/views}. */
final class ProjectFileWatcher implements AutoCloseable {

    interface Listener {
        void changed(Path path);

        void overflow();
    }

    private static final System.Logger LOG = System.getLogger(ProjectFileWatcher.class.getName());

    private final Path root;
    private final WatchService service;
    private final Listener listener;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    ProjectFileWatcher(Path root, Listener listener) throws IOException {
        this.root = ProjectPaths.normalize(root);
        this.listener = listener;
        this.service = root.getFileSystem().newWatchService();
        try {
            registerTree(this.root);
            // Остальные служебные данные не индексируются, но создание views внутри metadata должно быть видно.
            Path metadata = ProjectPaths.metadataDir(this.root);
            if (Files.isDirectory(metadata, LinkOption.NOFOLLOW_LINKS)) {
                register(metadata);
            }
            Path views = ProjectPaths.viewsDir(this.root);
            if (Files.isDirectory(views, LinkOption.NOFOLLOW_LINKS)) {
                register(views);
            }
        } catch (IOException | RuntimeException e) {
            service.close();
            throw e;
        }
        thread = new Thread(this::run, "mcs-project-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void registerTree(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (ProjectPaths.isExcluded(root, dir)) {
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
                boolean viewsDirectory = changed.equals(ProjectPaths.viewsDir(root));
                boolean viewFile = changed.getParent() != null
                        && changed.getParent().equals(ProjectPaths.viewsDir(root));
                if (ProjectPaths.isExcluded(root, changed) && !viewsDirectory && !viewFile) {
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
            LOG.log(System.Logger.Level.WARNING, "Cannot close project watcher", e);
        }
        thread.interrupt();
    }
}
