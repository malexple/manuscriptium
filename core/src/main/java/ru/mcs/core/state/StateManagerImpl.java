package ru.mcs.core.state;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import ru.mcs.spi.service.PersistentStateComponent;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.service.StateManager;

/**
 * Хранит состояние компонентов в YAML-файлах {@code <каталог области>/<id>.yml}. Записывает только изменившееся
 * (сравнение текста), атомарно (временный файл + move). При {@code persist == false} ничего не пишет.
 */
public final class StateManagerImpl {

    private static final System.Logger LOG = System.getLogger(StateManagerImpl.class.getName());
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]+");

    private static final class Entry {
        final String owner;
        final String id;
        final Scope scope;
        final PersistentStateComponent component;
        String lastText;

        Entry(String owner, String id, Scope scope, PersistentStateComponent component) {
            this.owner = owner;
            this.id = id;
            this.scope = scope;
            this.component = component;
        }
    }

    private final Path appDir;
    private Path vaultDir;
    private final boolean persist;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public StateManagerImpl(Path appDir, Path vaultDir, boolean persist) {
        this.appDir = appDir;
        this.vaultDir = vaultDir;
        this.persist = persist;
    }

    public synchronized void register(String owner, String id, Scope scope, PersistentStateComponent component) {
        if (id == null || !ID.matcher(id).matches() || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("Invalid state id: '" + id + "'");
        }
        if (entries.containsKey(id)) {
            throw new IllegalArgumentException("State component already registered: " + id);
        }
        Entry entry = new Entry(owner, id, scope, component);
        Path file = fileOf(entry);
        if (file != null && Files.isRegularFile(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                entry.lastText = text;
                component.loadState(StateYaml.parse(text));
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.WARNING, "Cannot load state '" + id + "' from " + file, t);
            }
        }
        entries.put(id, entry);
    }

    public synchronized void unregister(String owner, String id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return;
        }
        if (!entry.owner.equals(owner)) {
            throw new IllegalArgumentException("State '" + id + "' belongs to " + entry.owner);
        }
        save(entry);
        entries.remove(id);
    }

    public synchronized void unregisterOwner(String owner) {
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (entry.owner.equals(owner)) {
                save(entry);
                entries.remove(entry.id);
            }
        }
    }

    /**
     * Переключает область VAULT без записи в прежний каталог: вызывающий код заранее сохраняет старую сессию.
     * Зарегистрированные компоненты сначала получают пустое состояние, затем данные нового Vault.
     */
    public synchronized void changeVaultDirectory(Path directory) {
        vaultDir = directory == null ? null : directory.toAbsolutePath().normalize();
        for (Entry entry : entries.values()) {
            if (entry.scope != Scope.VAULT) {
                continue;
            }
            entry.lastText = null;
            Path file = fileOf(entry);
            try {
                componentReset(entry);
                if (file != null && Files.isRegularFile(file)) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    entry.lastText = text;
                    entry.component.loadState(StateYaml.parse(text));
                }
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.WARNING, "Cannot load state '" + entry.id + "' from " + file, t);
            }
        }
    }

    private static void componentReset(Entry entry) {
        entry.component.loadState(Map.of());
    }

    /** Записывает состояние всех компонентов, которое изменилось с прошлой записи. */
    public synchronized void saveChanged() {
        for (Entry entry : new ArrayList<>(entries.values())) {
            save(entry);
        }
    }

    public synchronized List<String> ids() {
        return entries.keySet().stream().sorted().toList();
    }

    public StateManager scopedFor(String owner) {
        StateManagerImpl self = this;
        return new StateManager() {
            @Override
            public void register(String id, Scope scope, PersistentStateComponent component) {
                self.register(owner, owner + "." + id, scope, component);
            }

            @Override
            public void unregister(String id) {
                self.unregister(owner, owner + "." + id);
            }
        };
    }

    private Path fileOf(Entry entry) {
        Path dir = entry.scope == Scope.APPLICATION ? appDir : vaultDir;
        return dir == null ? null : dir.resolve(entry.id + ".yml");
    }

    private void save(Entry entry) {
        if (!persist) {
            return;
        }
        Path file = fileOf(entry);
        if (file == null) {
            return;
        }
        String text;
        try {
            text = StateYaml.dump(entry.component.getState());
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "State '" + entry.id + "' cannot be serialised, skipped", t);
            return;
        }
        if (text.equals(entry.lastText)) {
            return;
        }
        if (entry.lastText == null && text.trim().equals("{}")) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            entry.lastText = text;
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot save state '" + entry.id + "' to " + file, e);
        }
    }
}
