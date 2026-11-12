package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultServiceTest {

    @TempDir
    Path tmp;

    @Test
    void watcherTracksCreateModifyAndRecursiveDelete() throws Exception {
        Files.writeString(tmp.resolve("initial.md"), "# Initial");
        try (VaultService service = VaultService.open(tmp, notes -> { })) {
            assertThat(service.notes()).extracting(VaultNote::path).containsExactly("initial.md");

            Files.createDirectories(tmp.resolve("new/nested"));
            Path created = tmp.resolve("new/nested/created.md");
            Files.writeString(created, "# Created");
            await(() -> contains(service.notes(), "new/nested/created.md"));
            service.awaitIdle();

            Files.writeString(created, "---\ntitle: Changed\n---\nbody");
            await(() -> service.notes().stream().anyMatch(n -> n.title().equals("Changed")));

            Files.delete(created);
            Files.delete(tmp.resolve("new/nested"));
            await(() -> !contains(service.notes(), "new/nested/created.md"));
            assertThat(service.notes()).extracting(VaultNote::path).containsExactly("initial.md");
        }
    }

    @Test
    void serviceDataStaysInsideVault() throws Exception {
        Files.writeString(tmp.resolve("note.md"), "note");
        try (VaultService ignored = VaultService.open(tmp, notes -> { })) {
            assertThat(VaultPaths.databaseFile(tmp)).exists();
            assertThat(tmp.resolve("note.md")).exists();
        }
    }

    private static boolean contains(List<VaultNote> notes, String path) {
        return notes.stream().anyMatch(note -> note.path().equals(path));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).as("watcher update before timeout").isTrue();
    }
}
