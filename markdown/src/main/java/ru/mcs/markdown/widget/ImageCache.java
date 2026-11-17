package ru.mcs.markdown.widget;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;

/** LRU по фактическим RGBA-байтам; декодирование всегда выполняется переданным executor. */
public final class ImageCache {

    @FunctionalInterface
    public interface Decoder {
        BufferedImage decode(Path path) throws Exception;
    }

    private static final int DEFAULT_MAX_BYTES = 64 * 1024 * 1024;

    private final Executor executor;
    private final Decoder decoder;
    private final long maxBytes;
    private final Runnable changeListener;
    private final LinkedHashMap<Path, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;
    private boolean disposed;

    public ImageCache(Executor executor, Runnable changeListener) {
        this(executor, DEFAULT_MAX_BYTES, path -> {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new IOException("Unsupported image: " + path);
            }
            return image;
        }, changeListener);
    }

    ImageCache(Executor executor, long maxBytes, Decoder decoder, Runnable changeListener) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.maxBytes = maxBytes;
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public Entry get(Path path) {
        Objects.requireNonNull(path, "path");
        final Entry entry;
        synchronized (this) {
            if (disposed) {
                return ImageCache.missing();
            }
            entry = entries.computeIfAbsent(path, ignored -> new Entry());
        }
        if (entry.startLoading()) {
            executor.execute(() -> load(path, entry));
        }
        return entry;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long bytes() {
        return bytes;
    }

    public synchronized void clear() {
        entries.values().forEach(entry -> entry.image = null);
        entries.clear();
        bytes = 0;
    }

    public synchronized void dispose() {
        disposed = true;
        clear();
    }

    private void load(Path path, Entry entry) {
        try {
            BufferedImage decoded = decoder.decode(path);
            long imageBytes = 4L * decoded.getWidth() * decoded.getHeight();
            synchronized (this) {
                if (!disposed && entries.get(path) == entry) {
                    entry.image = decoded;
                    entry.failed = false;
                    entry.bytes = imageBytes;
                    bytes += imageBytes;
                    evict();
                }
            }
        } catch (Exception error) {
            synchronized (this) {
                entry.failed = true;
            }
        } finally {
            entry.loading = false;
            changeListener.run();
        }
    }

    private void evict() {
        Iterator<Map.Entry<Path, Entry>> iterator = entries.entrySet().iterator();
        while (bytes > maxBytes && iterator.hasNext()) {
            Entry eldest = iterator.next().getValue();
            if (eldest.loading || eldest.image == null) {
                continue;
            }
            bytes -= eldest.bytes;
            eldest.image = null;
            iterator.remove();
        }
    }

    public static final class Entry {
        private volatile BufferedImage image;
        private volatile boolean failed;
        private volatile boolean loading;
        private long bytes;

        private boolean startLoading() {
            if (loading || image != null || failed) {
                return false;
            }
            loading = true;
            return true;
        }

        public BufferedImage image() {
            return image;
        }

        public boolean failed() {
            return failed;
        }

        public boolean loading() {
            return loading;
        }
    }

    public static Entry missing() {
        Entry entry = new Entry();
        entry.failed = true;
        return entry;
    }
}
