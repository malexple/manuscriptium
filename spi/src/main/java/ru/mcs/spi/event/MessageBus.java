package ru.mcs.spi.event;

/**
 * Шина сообщений: слабая связность между плагинами. Публикация синхронная, в потоке издателя;
 * ошибка одного слушателя не мешает остальным.
 */
public interface MessageBus {

    /** @return действие отписки */
    <L> Runnable subscribe(Topic<L> topic, L listener);

    /** Слушатель вызывается на EDT (из другого потока — через invokeLater). */
    <L> Runnable subscribeOnEdt(Topic<L> topic, L listener);

    /** Прокси: вызов метода рассылает его всем подписчикам темы. */
    <L> L publisher(Topic<L> topic);
}
