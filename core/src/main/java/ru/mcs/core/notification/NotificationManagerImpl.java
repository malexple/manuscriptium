package ru.mcs.core.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import ru.mcs.spi.ui.NotificationManager;
import ru.mcs.spi.ui.NotificationType;

/** Модель уведомлений: история (последние 100) и слушатели. Показ баллонов — в {@link NotificationLayer}. */
public final class NotificationManagerImpl {

    public static final int MAX_HISTORY = 100;

    public record Notice(Instant time, String source, String title, String content, NotificationType type) {
    }

    private final List<Notice> history = new ArrayList<>();
    private final List<Consumer<Notice>> listeners = new CopyOnWriteArrayList<>();

    public void notify(String source, String title, String content, NotificationType type) {
        Notice notice = new Notice(Instant.now(), source, title == null ? "" : title,
                content == null ? "" : content, type == null ? NotificationType.INFO : type);
        synchronized (this) {
            history.add(notice);
            while (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
        }
        for (Consumer<Notice> l : listeners) {
            l.accept(notice);
        }
    }

    public synchronized List<Notice> history() {
        return List.copyOf(history);
    }

    public Runnable addListener(Consumer<Notice> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public NotificationManager scopedFor(String source) {
        return (title, content, type) -> notify(source, title, content, type);
    }
}
