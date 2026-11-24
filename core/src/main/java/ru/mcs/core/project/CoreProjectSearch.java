package ru.mcs.core.project;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.spi.project.ProjectSearch;
import ru.mcs.spi.project.SearchHit;
import ru.mcs.spi.project.SearchQuery;

/** Связывает интерфейс поиска с текущим {@link ProjectService}; без Project поиск пуст. */
public final class CoreProjectSearch implements ProjectSearch {

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile ProjectService current;

    void setCurrent(ProjectService service) {
        current = service;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    @Override
    public boolean available() {
        return current != null;
    }

    @Override
    public CompletableFuture<List<SearchHit>> search(SearchQuery query) {
        ProjectService service = current;
        return service == null ? CompletableFuture.completedFuture(List.of()) : service.search(query);
    }

    @Override
    public Runnable addChangeListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
