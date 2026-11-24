package ru.mcs.spi.project;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Поиск по текущему Project для интерфейса поиска. Реализацию предоставляет платформа. */
public interface ProjectSearch {

    /** {@code true}, пока открыт Project. */
    boolean available();

    /** Выполняется вне EDT; без открытого Project завершается пустым списком. */
    CompletableFuture<List<SearchHit>> search(SearchQuery query);

    /** Вызывается при открытии, смене и закрытии Project (в EDT). Возвращает действие отписки. */
    Runnable addChangeListener(Runnable listener);
}
