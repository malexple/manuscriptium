package ru.mcs.spi.swing.ui;

import ru.mcs.spi.ui.StatusBarTask;
public interface StatusBar {

    /** @throws IllegalArgumentException если ID виджета занят */
    void addWidget(StatusBarWidget widget);

    void removeWidget(String id);

    /** Временное сообщение слева; исчезает через ~10 секунд. */
    void setMessage(String message);

    /** Показывает бегущий индикатор, пока задача не завершена. Потокобезопасно. */
    StatusBarTask startTask(String title);
}
