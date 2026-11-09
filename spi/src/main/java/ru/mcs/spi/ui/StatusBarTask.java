package ru.mcs.spi.ui;

/** Индикатор фоновой задачи в статус-баре. */
public interface StatusBarTask {

    void setTitle(String title);

    /** Идемпотентно: можно вызывать из любого потока. */
    void finish();
}
