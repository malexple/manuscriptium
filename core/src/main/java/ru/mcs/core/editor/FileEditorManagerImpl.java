package ru.mcs.core.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorManager;
import ru.mcs.spi.editor.FileEditorProvider;

/**
 * Модель области редактора: провайдеры, открытые вкладки (порядок: закреплённые первыми), выбор, сессия.
 * UI подписывается через {@link Listener}. Методы синхронизированы; UI-слушатели сами переходят на EDT.
 */
public final class FileEditorManagerImpl implements FileEditorManager {

    private static final System.Logger LOG = System.getLogger(FileEditorManagerImpl.class.getName());
    private static final String CORE_OWNER = "core";

    public enum CloseDecision { SAVE, DISCARD, CANCEL }

    public record TabInfo(Path file, FileEditor editor, boolean pinned) {
    }

    public interface Listener {
        default void tabsChanged() {
        }

        default void selectionChanged() {
        }

        default void stateChanged() {
        }
    }

    private record ProviderEntry(String owner, FileEditorProvider provider) {
    }

    private static final class Entry {
        final Path file;
        final FileEditor editor;
        final FileEditorProvider provider;
        boolean pinned;

        Entry(Path file, FileEditor editor, FileEditorProvider provider) {
            this.file = file;
            this.editor = editor;
            this.provider = provider;
        }
    }

    private final List<ProviderEntry> providers = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Path selected;
    private volatile Function<FileEditor, CloseDecision> confirmer = e -> CloseDecision.DISCARD;
    private volatile BiConsumer<Path, IOException> errorReporter = (p, e) -> { };

    // ---------- настройка ----------

    public void setCloseConfirmer(Function<FileEditor, CloseDecision> confirmer) {
        this.confirmer = confirmer;
    }

    public void setErrorReporter(BiConsumer<Path, IOException> reporter) {
        this.errorReporter = reporter;
    }

    public Runnable addListener(Listener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    // ---------- провайдеры ----------

    @Override
    public void registerProvider(FileEditorProvider provider) {
        registerProvider(CORE_OWNER, provider);
    }

    public synchronized void registerProvider(String owner, FileEditorProvider provider) {
        providers.add(new ProviderEntry(owner, provider));
    }

    @Override
    public synchronized void unregisterProvider(FileEditorProvider provider) {
        providers.removeIf(e -> e.provider() == provider);
        dropEditorsOf(Set.of(provider));
    }

    /** Выгрузка плагина: снять его провайдеры и закрыть их редакторы без подтверждения. */
    public synchronized void unregisterOwner(String owner) {
        Set<FileEditorProvider> removed = new HashSet<>();
        for (Iterator<ProviderEntry> it = providers.iterator(); it.hasNext(); ) {
            ProviderEntry e = it.next();
            if (e.owner().equals(owner)) {
                removed.add(e.provider());
                it.remove();
            }
        }
        dropEditorsOf(removed);
    }

    public synchronized List<String> providerIds() {
        return providers.stream().map(e -> e.provider().getId()).toList();
    }

    public FileEditorManager scopedFor(String owner) {
        FileEditorManagerImpl self = this;
        return new FileEditorManager() {
            @Override
            public void registerProvider(FileEditorProvider provider) {
                self.registerProvider(owner, provider);
            }

            @Override
            public void unregisterProvider(FileEditorProvider provider) {
                self.unregisterProvider(provider);
            }

            @Override
            public boolean openFile(Path file) {
                return self.openFile(file);
            }

            @Override
            public boolean closeFile(Path file) {
                return self.closeFile(file);
            }

            @Override
            public List<Path> getOpenFiles() {
                return self.getOpenFiles();
            }

            @Override
            public Optional<Path> getSelectedFile() {
                return self.getSelectedFile();
            }

            @Override
            public void selectFile(Path file) {
                self.selectFile(file);
            }

            @Override
            public boolean isPinned(Path file) {
                return self.isPinned(file);
            }

            @Override
            public void setPinned(Path file, boolean pinned) {
                self.setPinned(file, pinned);
            }

            @Override
            public void saveAll() throws IOException {
                self.saveAll();
            }
        };
    }

    private void dropEditorsOf(Set<FileEditorProvider> removed) {
        boolean any = false;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (removed.contains(e.provider)) {
                if (e.editor.isModified()) {
                    LOG.log(System.Logger.Level.WARNING, "Closing modified editor without saving: " + e.file);
                }
                dispose(e);
                it.remove();
                any = true;
            }
        }
        if (any) {
            if (find(selected) == null) {
                selected = entries.isEmpty() ? null : entries.get(0).file;
            }
            fire(Listener::tabsChanged);
            fire(Listener::selectionChanged);
        }
    }

    // ---------- открытие / закрытие ----------

    @Override
    public synchronized boolean openFile(Path file) {
        Path p = normalize(file);
        if (find(p) != null) {
            selectFile(p);
            return true;
        }
        FileEditorProvider provider = providers.stream()
                .map(ProviderEntry::provider)
                .filter(x -> safeAccept(x, p))
                .max(Comparator.comparingInt(FileEditorProvider::getPriority))
                .orElse(null);
        if (provider == null) {
            LOG.log(System.Logger.Level.WARNING, "No editor provider for " + p);
            return false;
        }
        FileEditor editor;
        try {
            editor = provider.createEditor(p);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.ERROR, "Cannot create editor for " + p, t);
            return false;
        }
        editor.addModifiedListener(() -> fire(Listener::stateChanged));
        entries.add(new Entry(p, editor, provider));
        selected = p;
        fire(Listener::tabsChanged);
        fire(Listener::selectionChanged);
        return true;
    }

    @Override
    public synchronized boolean closeFile(Path file) {
        Entry e = find(normalize(file));
        if (e == null) {
            return true;
        }
        if (e.editor.isModified() && !mayProceed(e)) {
            return false;
        }
        int index = entries.indexOf(e);
        entries.remove(e);
        dispose(e);
        if (e.file.equals(selected)) {
            selected = entries.isEmpty() ? null : entries.get(Math.min(index, entries.size() - 1)).file;
        }
        fire(Listener::tabsChanged);
        fire(Listener::selectionChanged);
        return true;
    }

    /** Проверка перед выходом: для каждого изменённого редактора спрашиваем решение. */
    public synchronized boolean confirmAll() {
        for (Entry e : new ArrayList<>(entries)) {
            if (e.editor.isModified() && !mayProceed(e)) {
                return false;
            }
        }
        return true;
    }

    private boolean mayProceed(Entry e) {
        return switch (confirmer.apply(e.editor)) {
            case CANCEL -> false;
            case DISCARD -> true;
            case SAVE -> trySave(e);
        };
    }

    private boolean trySave(Entry e) {
        try {
            e.editor.save();
            return true;
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.WARNING, "Save failed: " + e.file, ex);
            errorReporter.accept(e.file, ex);
            return false;
        }
    }

    @Override
    public synchronized void saveAll() throws IOException {
        for (Entry e : new ArrayList<>(entries)) {
            if (e.editor.isModified()) {
                e.editor.save();
            }
        }
    }

    public synchronized boolean hasModified() {
        return entries.stream().anyMatch(e -> e.editor.isModified());
    }

    // ---------- выбор / закрепление ----------

    @Override
    public synchronized void selectFile(Path file) {
        Path p = normalize(file);
        if (find(p) != null && !p.equals(selected)) {
            selected = p;
            fire(Listener::selectionChanged);
        }
    }

    @Override
    public synchronized Optional<Path> getSelectedFile() {
        return Optional.ofNullable(selected);
    }

    public synchronized Optional<FileEditor> selectedEditor() {
        Entry e = find(selected);
        return e == null ? Optional.empty() : Optional.of(e.editor);
    }

    @Override
    public synchronized boolean isPinned(Path file) {
        Entry e = find(normalize(file));
        return e != null && e.pinned;
    }

    @Override
    public synchronized void setPinned(Path file, boolean pinned) {
        Entry e = find(normalize(file));
        if (e == null || e.pinned == pinned) {
            return;
        }
        entries.remove(e);
        e.pinned = pinned;
        int index = (int) entries.stream().filter(x -> x.pinned).count();
        entries.add(index, e);
        fire(Listener::tabsChanged);
    }

    @Override
    public synchronized List<Path> getOpenFiles() {
        return entries.stream().map(e -> e.file).toList();
    }

    public synchronized List<TabInfo> tabs() {
        return entries.stream().map(e -> new TabInfo(e.file, e.editor, e.pinned)).toList();
    }

    // ---------- сессия ----------

    public synchronized SessionStore.Session currentSession() {
        List<SessionStore.Tab> tabs = entries.stream()
                .map(e -> new SessionStore.Tab(e.file, e.pinned))
                .toList();
        return new SessionStore.Session(tabs, selected);
    }

    /** Открывает сохранённые файлы, которые ещё существуют и для которых есть провайдер. */
    public synchronized void restoreSession(SessionStore.Session session) {
        for (SessionStore.Tab tab : session.tabs()) {
            if (Files.isRegularFile(tab.file()) && openFile(tab.file()) && tab.pinned()) {
                setPinned(tab.file(), true);
            }
        }
        if (session.selected() != null) {
            selectFile(session.selected());
        }
    }

    // ---------- внутреннее ----------

    private Entry find(Path normalized) {
        if (normalized == null) {
            return null;
        }
        for (Entry e : entries) {
            if (e.file.equals(normalized)) {
                return e;
            }
        }
        return null;
    }

    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }

    private static boolean safeAccept(FileEditorProvider provider, Path file) {
        try {
            return provider.accept(file);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "Provider accept failed: " + provider.getId(), t);
            return false;
        }
    }

    private static void dispose(Entry e) {
        try {
            e.editor.dispose();
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "Editor dispose failed: " + e.file, t);
        }
    }

    private void fire(Consumer<Listener> call) {
        for (Listener l : listeners) {
            try {
                call.accept(l);
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.ERROR, "Editor listener failed", ex);
            }
        }
    }
}
