package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultServiceTest {

    @TempDir
    Path tmp;

    @Test
    void watcherTracksCreateModifyAndRecursiveDelete() throws Exception {
        Files.writeString(tmp.resolve("initial.md"), "# Initial");
        try (VaultService service = VaultService.open(tmp, snapshot -> { })) {
            assertThat(service.notes()).extracting(VaultNote::path).containsExactly("initial.md");

            Files.createDirectories(tmp.resolve("new/nested"));
            Path created = tmp.resolve("new/nested/created.md");
            Files.writeString(created, "# Created");
            await(() -> contains(service.notes(), "new/nested/created.md"));
            service.awaitIdle();
            assertThat(service.search(search("Created", VaultSearchScope.TITLE)).get(5, TimeUnit.SECONDS))
                    .extracting(VaultSearchResult::path).containsExactly("new/nested/created.md");

            Files.writeString(created, "---\ntitle: Changed\n---\nbody");
            await(() -> service.notes().stream().anyMatch(n -> n.title().equals("Changed")));
            assertThat(service.search(search("Changed", VaultSearchScope.TITLE)).get(5, TimeUnit.SECONDS))
                    .extracting(VaultSearchResult::path).containsExactly("new/nested/created.md");

            Files.delete(created);
            Files.delete(tmp.resolve("new/nested"));
            await(() -> !contains(service.notes(), "new/nested/created.md"));
            assertThat(service.search(search("Changed", VaultSearchScope.ALL)).get(5, TimeUnit.SECONDS)).isEmpty();
            assertThat(service.notes()).extracting(VaultNote::path).containsExactly("initial.md");
        }
    }

    @Test
    void serviceDataStaysInsideVault() throws Exception {
        Files.writeString(tmp.resolve("note.md"), "note");
        try (VaultService ignored = VaultService.open(tmp, snapshot -> { })) {
            assertThat(VaultPaths.databaseFile(tmp)).exists();
            assertThat(VaultPaths.luceneDir(tmp)).isDirectory();
            assertThat(tmp.resolve("note.md")).exists();
        }
    }

    @Test
    void watcherReloadsExternalViewsAndKeepsValidOnesAfterYamlError() throws Exception {
        Files.writeString(tmp.resolve("note.md"), "# Note");
        try (VaultService service = VaultService.open(tmp, snapshot -> { })) {
            Path view = VaultPaths.viewsDir(tmp).resolve("tree.yml");
            Files.writeString(view, "name: First\nentries:\n  - file: note.md\n");
            await(() -> service.snapshot().views().size() == 1
                    && service.snapshot().views().getFirst().name().equals("First"));

            Files.writeString(view, "name: Changed\nentries:\n  - file: note.md\n");
            await(() -> service.snapshot().views().size() == 1
                    && service.snapshot().views().getFirst().name().equals("Changed"));

            Files.writeString(VaultPaths.viewsDir(tmp).resolve("broken.yml"), "name: [\n");
            await(() -> !service.snapshot().viewErrors().isEmpty()
                    && service.snapshot().views().size() == 1
                    && service.snapshot().views().getFirst().name().equals("Changed"));
            assertThat(service.snapshot().views()).extracting(ViewDefinition::name).containsExactly("Changed");
            assertThat(service.snapshot().notes()).extracting(VaultNote::path).containsExactly("note.md");
        }
    }

    @Test
    void moveAndClonePersistOnlyYamlLinks() throws Exception {
        Path note = tmp.resolve("note.md");
        Files.writeString(note, "# Note");
        Files.createDirectories(VaultPaths.viewsDir(tmp));
        Files.writeString(VaultPaths.viewsDir(tmp).resolve("tree.yml"), """
                name: Tree
                entries:
                  - node: Source
                    children:
                      - file: note.md
                  - node: Target
                    children: []
                """);
        try (VaultService service = VaultService.open(tmp, snapshot -> { })) {
            assertThat(service.moveOrClone("tree", List.of(0, 0), "stale.md", List.of(1), true).get()).isFalse();
            assertThat(service.snapshot().views().getFirst().entries().get(1).children()).isEmpty();
            assertThat(service.moveOrClone("tree", List.of(0, 0), List.of(1), true).get()).isTrue();
            ViewDefinition cloned = service.snapshot().views().getFirst();
            assertThat(cloned.entries().get(0).children()).hasSize(1);
            assertThat(cloned.entries().get(1).children()).hasSize(1);
            assertThat(ViewOperations.clonedFiles(service.snapshot().views())).containsExactly("note.md");
            assertThat(note).exists();

            assertThat(service.moveOrClone("tree", List.of(0, 0), List.of(), false).get()).isTrue();
            ViewDefinition moved = service.snapshot().views().getFirst();
            assertThat(moved.entries().get(0).children()).isEmpty();
            assertThat(moved.entries().get(1).children()).hasSize(1);
            assertThat(moved.entries().get(2).file()).isEqualTo("note.md");
            assertThat(note).exists();
            service.awaitIdle();
        }

        ViewStore.LoadResult reloaded = new ViewStore(tmp).load();
        assertThat(reloaded.errors()).isEmpty();
        assertThat(reloaded.views().getFirst().entries().get(0).children()).isEmpty();
        assertThat(reloaded.views().getFirst().entries().get(1).children()).hasSize(1);
        assertThat(reloaded.views().getFirst().entries().get(2).file()).isEqualTo("note.md");
    }

    @Test
    void addLinkCreatesFirstViewWithoutOverwritingYamlAndReusesExistingTargets() throws Exception {
        Files.writeString(tmp.resolve("note.md"), "# Note");
        Files.createDirectories(VaultPaths.viewsDir(tmp));
        Path occupied = VaultPaths.viewsDir(tmp).resolve("classification.yml");
        Files.writeString(occupied, "name: [\n");
        Files.writeString(VaultPaths.viewsDir(tmp).resolve("existing.yml"), """
                name: Existing
                entries:
                  - node: Target
                    children: []
                """);
        try (VaultService service = VaultService.open(tmp, snapshot -> { })) {
            String createdId = service.addLink("note.md", null, "Classification", List.of()).get();
            assertThat(createdId).isEqualTo("classification-2");
            assertThat(Files.readString(occupied)).isEqualTo("name: [\n");
            ViewDefinition created = service.snapshot().views().stream()
                    .filter(view -> view.id().equals(createdId)).findFirst().orElseThrow();
            assertThat(created.entries()).extracting(ViewEntry::file).containsExactly("note.md");
            assertThat(service.addLink("note.md", createdId, null, List.of()).get()).isNull();

            assertThat(service.addLink("note.md", "existing", null, List.of()).get()).isEqualTo("existing");
            assertThat(service.addLink("note.md", "existing", null, List.of()).get()).isNull();
            assertThat(service.addLink("note.md", "existing", null, List.of(0)).get()).isEqualTo("existing");
            ViewDefinition existing = service.snapshot().views().stream()
                    .filter(view -> view.id().equals("existing")).findFirst().orElseThrow();
            assertThat(existing.entries().getFirst().children()).extracting(ViewEntry::file).containsExactly("note.md");
            assertThat(existing.entries().get(1).file()).isEqualTo("note.md");

            String movedId = service.moveLink("note.md", createdId, List.of(0),
                    null, "Second", List.of()).get();
            assertThat(movedId).isEqualTo("second");
            assertThat(service.snapshot().views().stream().filter(view -> view.id().equals(createdId))
                    .findFirst().orElseThrow().entries()).isEmpty();
            assertThat(service.snapshot().views().stream().filter(view -> view.id().equals("second"))
                    .findFirst().orElseThrow().entries()).extracting(ViewEntry::file).containsExactly("note.md");

            assertThat(service.moveLink("note.md", "second", List.of(0),
                    createdId, null, List.of()).get()).isEqualTo(createdId);
            assertThat(service.snapshot().views().stream().filter(view -> view.id().equals("second"))
                    .findFirst().orElseThrow().entries()).isEmpty();
            assertThat(service.moveLink("note.md", "second", List.of(0),
                    createdId, null, List.of()).get()).isNull();
            assertThat(service.addLink("missing.md", createdId, null, List.of()).get()).isNull();
            assertThat(tmp.resolve("note.md")).exists();
        }

        ViewDefinition persisted = new ViewStore(tmp).load().views().stream()
                .filter(view -> view.id().equals("classification-2")).findFirst().orElseThrow();
        assertThat(persisted.entries().getFirst().file()).isEqualTo("note.md");
        ViewDefinition persistedExisting = new ViewStore(tmp).load().views().stream()
                .filter(view -> view.id().equals("existing")).findFirst().orElseThrow();
        assertThat(persistedExisting.entries().getFirst().children()).extracting(ViewEntry::file)
                .containsExactly("note.md");
        assertThat(persistedExisting.entries().get(1).file()).isEqualTo("note.md");
    }

    private static VaultSearchQuery search(String text, VaultSearchScope scope) {
        return new VaultSearchQuery(text, scope, "", null, 100);
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
