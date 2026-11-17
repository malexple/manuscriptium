package ru.mcs.core.project;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.spi.project.IndexContributor;
import ru.mcs.spi.project.IndexContributorRegistry;

/** Потокобезопасный реестр языковых вкладов; чтение выдаёт стабильный снимок. */
public final class ProjectIndexContributors implements IndexContributorRegistry {

    private final CopyOnWriteArrayList<IndexContributor> contributors = new CopyOnWriteArrayList<>();

    @Override
    public void register(IndexContributor contributor) {
        contributors.addIfAbsent(java.util.Objects.requireNonNull(contributor));
    }

    @Override
    public void unregister(IndexContributor contributor) {
        contributors.remove(contributor);
    }

    @Override
    public List<IndexContributor> contributors() {
        return List.copyOf(contributors);
    }
}
