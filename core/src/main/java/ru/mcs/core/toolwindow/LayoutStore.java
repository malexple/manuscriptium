package ru.mcs.core.toolwindow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import ru.mcs.spi.ui.ToolWindowAnchor;

/**
 * Простое хранилище раскладки (видимость панелей и размеры сторон) в .properties.
 * В срезе 5 заменится на PersistentStateComponent.
 */
public final class LayoutStore {

    private static final System.Logger LOG = System.getLogger(LayoutStore.class.getName());

    private final Path file;
    private final Properties props = new Properties();

    private LayoutStore(Path file) {
        this.file = file;
    }

    public static LayoutStore inMemory() {
        return new LayoutStore(null);
    }

    public static LayoutStore at(Path file) {
        LayoutStore store = new LayoutStore(file);
        store.load();
        return store;
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot read layout: " + file, e);
        }
    }

    public synchronized Optional<Boolean> visible(String id) {
        String v = props.getProperty("tw." + id + ".visible");
        return v == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(v));
    }

    public synchronized void setVisible(String id, boolean visible) {
        props.setProperty("tw." + id + ".visible", String.valueOf(visible));
    }

    public synchronized int size(ToolWindowAnchor anchor, int fallback) {
        try {
            return Integer.parseInt(props.getProperty("size." + anchor.name(), String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public synchronized void setSize(ToolWindowAnchor anchor, int size) {
        props.setProperty("size." + anchor.name(), String.valueOf(size));
    }

    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = parent.resolve(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "Manuscriptium layout");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot save layout: " + file, e);
        }
    }
}
