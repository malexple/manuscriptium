package ru.mcs.spi.project;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Фоновая статистика физической ветки или видимой проекции view. */
public interface BranchStatisticsService {

    CompletableFuture<Statistics> physical(Path selected, List<Path> files);

    CompletableFuture<Statistics> view(Path root, ViewDefinition view, List<Integer> nodePath);

    record Statistics(long words, long characters, long files) {
        public long readingMinutes() {
            return words == 0 ? 0 : (words + 199) / 200;
        }
    }
}
