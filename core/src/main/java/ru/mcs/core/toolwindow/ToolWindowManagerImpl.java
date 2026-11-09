package ru.mcs.core.toolwindow;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.ui.ToolWindow;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.ui.ToolWindowManager;
import ru.mcs.spi.ui.ToolWindowSpec;

/**
 * Модель панелей: реестр, активная панель на каждой стороне, размеры. Сохранение — через {@link LayoutStore}
 * (он зарегистрирован в StateManager). Не содержит Swing-компонентов (кроме ленивых фабрик содержимого).
 */
public final class ToolWindowManagerImpl {

    private static final int DEFAULT_SIDE = 280;
    private static final int DEFAULT_BOTTOM = 200;

    private final ActionManagerImpl actions;
    private final LayoutStore store;
    private final Map<String, ToolWindowImpl> windows = new LinkedHashMap<>();
    private final Map<ToolWindowAnchor, ToolWindowImpl> active = new EnumMap<>(ToolWindowAnchor.class);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public ToolWindowManagerImpl(ActionManagerImpl actions, LayoutStore store) {
        this.actions = actions;
        this.store = store;
    }

    public synchronized ToolWindowImpl register(String owner, ToolWindowSpec spec) {
        if (windows.containsKey(spec.id())) {
            throw new IllegalArgumentException("Tool window already registered: " + spec.id());
        }
        ToolWindowImpl tw = new ToolWindowImpl(this, owner, spec);
        registerToggleAction(owner, tw);
        windows.put(spec.id(), tw);
        boolean wantVisible = store.visible(spec.id()).orElse(spec.showByDefault());
        if (wantVisible && !active.containsKey(spec.anchor())) {
            active.put(spec.anchor(), tw);
        }
        fireChanged();
        return tw;
    }

    public synchronized ToolWindowImpl get(String id) {
        return windows.get(id);
    }

    public synchronized void unregister(String owner, String id) {
        ToolWindowImpl tw = windows.get(id);
        if (tw == null) {
            return;
        }
        if (!tw.owner().equals(owner)) {
            throw new IllegalArgumentException("Tool window '" + id + "' belongs to " + tw.owner());
        }
        remove(tw);
        fireChanged();
    }

    /** Снять все панели владельца (выгрузка плагина). Сохранённая видимость не меняется. */
    public synchronized void unregisterOwner(String owner) {
        for (ToolWindowImpl tw : new ArrayList<>(windows.values())) {
            if (tw.owner().equals(owner)) {
                remove(tw);
            }
        }
        fireChanged();
    }

    private void remove(ToolWindowImpl tw) {
        windows.remove(tw.getId());
        active.remove(tw.getAnchor(), tw);
        actions.scopedFor(tw.owner()).unregisterAction(toggleActionId(tw));
    }

    private static String toggleActionId(ToolWindowImpl tw) {
        return "toolwindow.toggle." + tw.getId();
    }

    private void registerToggleAction(String owner, ToolWindowImpl tw) {
        ActionManager scoped = actions.scopedFor(owner);
        String actionId = toggleActionId(tw);
        scoped.registerAction(actionId, new AnAction("Tool Window: " + tw.getTitle()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                tw.toggle();
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setText((tw.isVisible() ? "Hide " : "Show ") + tw.getTitle());
            }
        });
        scoped.addToGroup(ActionIds.VIEW_MENU, actionId, Constraints.LAST);
    }

    synchronized boolean isVisible(ToolWindowImpl tw) {
        return active.get(tw.getAnchor()) == tw;
    }

    synchronized void show(ToolWindowImpl tw) {
        ToolWindowImpl previous = active.put(tw.getAnchor(), tw);
        if (previous == tw) {
            return;
        }
        if (previous != null) {
            store.setVisible(previous.getId(), false);
        }
        store.setVisible(tw.getId(), true);
        fireChanged();
    }

    synchronized void hide(ToolWindowImpl tw) {
        if (active.get(tw.getAnchor()) != tw) {
            return;
        }
        active.remove(tw.getAnchor());
        store.setVisible(tw.getId(), false);
        fireChanged();
    }

    public synchronized List<ToolWindowImpl> windows() {
        return new ArrayList<>(windows.values());
    }

    public synchronized ToolWindowImpl activeAt(ToolWindowAnchor anchor) {
        return active.get(anchor);
    }

    public int size(ToolWindowAnchor anchor) {
        return store.size(anchor, anchor == ToolWindowAnchor.BOTTOM ? DEFAULT_BOTTOM : DEFAULT_SIDE);
    }

    public void setSize(ToolWindowAnchor anchor, int size) {
        store.setSize(anchor, size);
    }

    public List<String> ids() {
        return windows().stream().map(ToolWindowImpl::getId).toList();
    }

    public ToolWindowManager scopedFor(String owner) {
        return new ToolWindowManager() {
            @Override
            public ToolWindow register(ToolWindowSpec spec) {
                return ToolWindowManagerImpl.this.register(owner, spec);
            }

            @Override
            public ToolWindow get(String id) {
                return ToolWindowManagerImpl.this.get(id);
            }

            @Override
            public void unregister(String id) {
                ToolWindowManagerImpl.this.unregister(owner, id);
            }
        };
    }

    /** Слушатель вызывается в потоке, изменившем состояние; UI сам переходит на EDT. */
    public Runnable addChangeListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    void fireChanged() {
        for (Runnable l : listeners) {
            l.run();
        }
    }
}
