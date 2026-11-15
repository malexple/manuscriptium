package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.service.AtomicFileStore;

class BranchStatisticsTest {

    @TempDir
    Path root;

    @Test
    void physicalBranchReadsOutsideEdtAndCountsUtf16Characters() throws Exception {
        Path one = Files.writeString(root.resolve("one.md"), "One two 🙂");
        Path directory = Files.createDirectory(root.resolve("part"));
        Path two = Files.writeString(directory.resolve("two.md"), "three_four 4");
        Files.writeString(root.resolve("outside.txt"), "not indexed");
        List<VaultNote> notes = List.of(
                new VaultNote(one, "one.md", "One"),
                new VaultNote(two, "part/two.md", "Two"));
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
                new Thread(runnable, "branch-statistics-test"));
        AtomicFileStore store = new AtomicFileStore() {
            @Override
            public byte[] read(Path file) throws IOException {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
                return Files.readAllBytes(file);
            }

            @Override
            public void write(Path file, byte[] content) {
                throw new UnsupportedOperationException();
            }
        };
        BranchStatistics statistics = new BranchStatistics(store, executor);
        try {
            AtomicReference<CompletableFuture<BranchStatistics.Statistics>> calculation = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> calculation.set(statistics.physical(root, notes)));
            BranchStatistics.Statistics whole = calculation.get().get(5, TimeUnit.SECONDS);
            assertThat(whole.words()).isEqualTo(4);
            assertThat(whole.characters()).isEqualTo(22);
            assertThat(whole.files()).isEqualTo(2);
            assertThat(whole.readingMinutes()).isEqualTo(1);

            BranchStatistics.Statistics part = statistics.physical(directory, notes).get(5, TimeUnit.SECONDS);
            assertThat(part.words()).isEqualTo(2);
            assertThat(part.characters()).isEqualTo(12);
            assertThat(part.files()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void viewBranchUsesVisibleBookProjectionAndSupportsSubtree() throws Exception {
        Files.writeString(root.resolve("one.md"), "one two");
        Files.writeString(root.resolve("hidden.md"), "hidden words must not count");
        Files.writeString(root.resolve("two.md"), "🙂 three");
        ViewDefinition view = new ViewDefinition("book", "Book", root.resolve("book.yml"), List.of(
                ViewEntry.node("Part", null, true, List.of(), List.of(
                        ViewEntry.file("one.md", null, true, List.of()),
                        ViewEntry.node("Hidden", null, false, List.of(), List.of(
                                ViewEntry.file("two.md", null, true, List.of()))))),
                ViewEntry.file("hidden.md", null, false, List.of()),
                ViewEntry.file("two.md", null, true, List.of())));
        BranchStatistics statistics = new BranchStatistics(new ru.mcs.core.service.AtomicFileStoreImpl(),
                Runnable::run);

        BranchStatistics.Statistics whole = statistics.view(root, view, List.of()).join();
        assertThat(whole.words()).isEqualTo(3);
        assertThat(whole.characters()).isEqualTo(15);
        assertThat(whole.files()).isEqualTo(2);

        BranchStatistics.Statistics part = statistics.view(root, view, List.of(0)).join();
        assertThat(part.words()).isEqualTo(2);
        assertThat(part.files()).isEqualTo(1);
    }

    @Test
    void readingTimeRoundsUpAtTwoHundredWordsPerMinute() throws Exception {
        Path file = Files.writeString(root.resolve("long.md"),
                String.join(" ", Collections.nCopies(401, "word")));
        BranchStatistics statistics = new BranchStatistics(new ru.mcs.core.service.AtomicFileStoreImpl(),
                Runnable::run);

        BranchStatistics.Statistics result = statistics.physical(root,
                List.of(new VaultNote(file, "long.md", "Long"))).join();

        assertThat(result.words()).isEqualTo(401);
        assertThat(result.readingMinutes()).isEqualTo(3);
    }
}
