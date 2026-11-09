package ru.mcs.core.statusbar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.spi.ui.StatusBar;
import ru.mcs.spi.ui.StatusBarTask;
import ru.mcs.spi.ui.StatusBarWidget;

/** Модель статус-бара (без Swing): виджеты, сообщение, фоновые задачи. Работает и в headless-режиме. */
public final class StatusBarModel {

    private static final System.Logger LOG = System.getLogger(StatusBarModel.class.getName());

    public record Entry(String owner, StatusBarWidget widget) {
    }

    public final class Task implements StatusBarTask {
        private final String owner;
        private volatile String title;
        private volatile boolean finished;

        private Task(String owner, String title) {
            this.owner = owner;
            this.title = title;
        }

        public String title() {
            return title;
        }

        @Override
        public void setTitle(String title) {
            this.title = title;
            fireChanged();
        }

        @Override
        public void finish() {
            boolean removed;
            synchronized (StatusBarModel.this) {
                finished = true;
                removed = tasks.remove(this);
            }
            if (removed) {
                fireChanged();
            }
        }

        public boolean isFinished() {
            return finished;
        }
    }

    private final List<Entry> widgets = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile String message = "";

    public synchronized void addWidget(String owner, StatusBarWidget widget) {
        for (Entry e : widgets) {
            if (e.widget().getId().equals(widget.getId())) {
                throw new IllegalArgumentException("Status bar widget already registered: " + widget.getId());
            }
        }
        widgets.add(new Entry(owner, widget));
        fireChanged();
    }

    public synchronized void removeWidget(String owner, String id) {
        for (Iterator<Entry> it = widgets.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.widget().getId().equals(id)) {
                if (!e.owner().equals(owner)) {
                    throw new IllegalArgumentException("Widget '" + id + "' belongs to " + e.owner());
                }
                it.remove();
                dispose(e.widget());
                fireChanged();
                return;
            }
        }
    }

    /** Выгрузка плагина: убрать его виджеты и завершить его задачи. */
    public synchronized void removeOwner(String owner) {
        boolean changed = false;
        for (Iterator<Entry> it = widgets.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.owner().equals(owner)) {
                it.remove();
                dispose(e.widget());
                changed = true;
            }
        }
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.owner.equals(owner)) {
                t.finished = true;
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            fireChanged();
        }
    }

    public void setMessage(String text) {
        this.message = text == null ? "" : text;
        fireChanged();
    }

    public String message() {
        return message;
    }

    public synchronized Task startTask(String owner, String title) {
        Task task = new Task(owner, title);
        tasks.add(task);
        fireChanged();
        return task;
    }

    public synchronized List<Entry> widgets() {
        return List.copyOf(widgets);
    }

    public synchronized List<Task> tasks() {
        return List.copyOf(tasks);
    }

    public List<String> widgetIds() {
        return widgets().stream().map(e -> e.widget().getId()).toList();
    }

    public Runnable addChangeListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void fireChanged() {
        for (Runnable l : listeners) {
            l.run();
        }
    }

    private static void dispose(StatusBarWidget widget) {
        try {
            widget.dispose();
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "Widget dispose failed: " + widget.getId(), t);
        }
    }

    public StatusBar scopedFor(String owner) {
        StatusBarModel self = this;
        return new StatusBar() {
            @Override
            public void addWidget(StatusBarWidget widget) {
                self.addWidget(owner, widget);
            }

            @Override
            public void removeWidget(String id) {
                self.removeWidget(owner, id);
            }

            @Override
            public void setMessage(String message) {
                self.setMessage(message);
            }

            @Override
            public StatusBarTask startTask(String title) {
                return self.startTask(owner, title);
            }
        };
    }
}
