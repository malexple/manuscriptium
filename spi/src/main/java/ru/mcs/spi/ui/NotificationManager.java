package ru.mcs.spi.ui;

/** Уведомления-баллоны в правом нижнем углу + история в панели Notifications. INFO/WARNING исчезают сами, ERROR — по клику. */
public interface NotificationManager {

    void notify(String title, String content, NotificationType type);

    default void info(String title, String content) {
        notify(title, content, NotificationType.INFO);
    }

    default void warn(String title, String content) {
        notify(title, content, NotificationType.WARNING);
    }

    default void error(String title, String content) {
        notify(title, content, NotificationType.ERROR);
    }
}
