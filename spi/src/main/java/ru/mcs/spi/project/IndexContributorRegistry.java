package ru.mcs.spi.project;

import java.util.List;

/** Отдельный multi-service реестр: один языковой модуль не перезаписывает вклады остальных. */
public interface IndexContributorRegistry {

    void register(IndexContributor contributor);

    void unregister(IndexContributor contributor);

    List<IndexContributor> contributors();
}
