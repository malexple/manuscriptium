package ru.mcs.core.editor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Сессия редактора: открытые вкладки, закрепления и активная вкладка. Позже перейдёт на PersistentState. */
public final class SessionStore {

    private static final System.Logger LOG = System.getLogger(SessionStore.class.getName());

    public record Tab(Path file, boolean pinned) {
    }

    public record Session(List<Tab> tabs, Path selected) {
    }

    private final Path file;
    private Session memory = new Session(List.of(), null);

    private SessionStore(Path file) {
        this.file = file;
    }

    public static SessionStore inMemory() {
        return new SessionStore(null);
    }

    public static SessionStore at(Path file) {
        return new SessionStore(file);
    }

    public synchronized Session load() {
        if (file == null) {
            return memory;
        }
        if (!Files.isRegularFile(file)) {
            return new Session(List.of(), null);
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot read session: " + file, e);
            return new Session(List.of(), null);
        }
        List<Tab> tabs = new ArrayList<>();
        int count = parseInt(p.getProperty("count"));
        for (int i = 0; i < count; i++) {
            Path path = toPath(p.getProperty("open." + i));
            if (path != null) {
                tabs.add(new Tab(path, Boolean.parseBoolean(p.getProperty("open." + i + ".pinned"))));
            }
        }
        return new Session(tabs, toPath(p.getProperty("selected")));
    }

    public synchronized void save(Session session) {
        if (file == null) {
            memory = session;
            return;
        }
        Properties p = new Properties();
        p.setProperty("count", String.valueOf(session.tabs().size()));
        for (int i = 0; i < session.tabs().size(); i++) {
            p.setProperty("open." + i, session.tabs().get(i).file().toString());
            p.setProperty("open." + i + ".pinned", String.valueOf(session.tabs().get(i).pinned()));
        }
        if (session.selected() != null) {
            p.setProperty("selected", session.selected().toString());
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = parent.resolve(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "Manuscriptium session");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot save session: " + file, e);
        }
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Path toPath(String s) {
        try {
            return s == null ? null : Path.of(s);
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
