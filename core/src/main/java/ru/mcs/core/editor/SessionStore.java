package ru.mcs.core.editor;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import ru.mcs.spi.service.PersistentStateComponent;

/**
 * Сессия редактора (id core.session): открытые вкладки, закрепления, активная вкладка.
 * До {@link #activate} возвращает прочитанное состояние (чтобы автосохранение не затёрло его, пока плагины ещё грузятся);
 * после — актуальное состояние из редактора.
 */
public final class SessionStore implements PersistentStateComponent {

    public record Tab(Path file, boolean pinned) {
    }

    public record Session(List<Tab> tabs, Path selected) {
    }

    private volatile Session loaded = new Session(List.of(), null);
    private volatile Supplier<Session> live;

    public Session loaded() {
        return loaded;
    }

    public void activate(Supplier<Session> liveSession) {
        this.live = liveSession;
    }

    /** Перед сменой Vault live-поставщик отключается, чтобы закрытие старых вкладок не затёрло новую сессию. */
    public void deactivate() {
        this.live = null;
    }

    @Override
    public Map<String, Object> getState() {
        Supplier<Session> source = live;
        Session session = source != null ? source.get() : loaded;
        List<Object> tabs = new ArrayList<>();
        for (Tab tab : session.tabs()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("file", tab.file().toString());
            t.put("pinned", tab.pinned());
            tabs.add(t);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("tabs", tabs);
        if (session.selected() != null) {
            state.put("selected", session.selected().toString());
        }
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        List<Tab> tabs = new ArrayList<>();
        if (state.get("tabs") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Path path = toPath(m.get("file"));
                    if (path != null) {
                        tabs.add(new Tab(path, Boolean.TRUE.equals(m.get("pinned"))));
                    }
                }
            }
        }
        loaded = new Session(tabs, toPath(state.get("selected")));
    }

    private static Path toPath(Object value) {
        if (!(value instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            return Path.of(s);
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
