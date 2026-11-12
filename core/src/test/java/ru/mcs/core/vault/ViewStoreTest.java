package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViewStoreTest {

    @TempDir
    Path tmp;

    @Test
    void loadsHumanReadableViewWithDefaultsAndMetadata() throws Exception {
        write("classification.yml", """
                name: Классификация
                entries:
                  - node: Научная литература
                    override-title: Наука
                    visibility: true
                    tags: [science]
                    children:
                      - node: Java
                        children:
                          - file: notes/java.md
                            override-title: Java 21
                            visibility: true
                            tags: [programming]
                  - file: plain.md
                """);

        ViewStore.LoadResult result = new ViewStore(tmp).load();

        assertThat(result.errors()).isEmpty();
        assertThat(result.views()).hasSize(1);
        ViewDefinition view = result.views().getFirst();
        assertThat(view.id()).isEqualTo("classification");
        assertThat(view.name()).isEqualTo("Классификация");
        ViewEntry science = view.entries().getFirst();
        assertThat(science.isNode()).isTrue();
        assertThat(science.title()).isEqualTo("Наука");
        assertThat(science.tags()).containsExactly("science");
        ViewEntry java = science.children().getFirst().children().getFirst();
        assertThat(java.file()).isEqualTo("notes/java.md");
        assertThat(java.title()).isEqualTo("Java 21");
        assertThat(java.visibility()).isTrue();
        assertThat(view.entries().get(1).visibility()).isTrue();
        assertThat(view.entries().get(1).tags()).isEmpty();
    }

    @Test
    void brokenViewDoesNotHideValidViews() throws Exception {
        write("a-broken.yml", "name: Broken\nentries: [\n");
        write("b-unknown.yml", "name: Unknown\nunknown: true\n");
        write("c-valid.yml", "name: Valid\nentries:\n  - file: note.md\n");

        ViewStore.LoadResult result = new ViewStore(tmp).load();

        assertThat(result.views()).extracting(ViewDefinition::id).containsExactly("c-valid");
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).startsWith("a-broken.yml:");
        assertThat(result.errors().get(1)).startsWith("b-unknown.yml:").contains("unknown key");
    }

    @Test
    void rejectsInvalidEntryShapesAndPaths() throws Exception {
        List<String> documents = List.of(
                "name: V\nentries:\n  - node: N\n    file: a.md\n",
                "name: V\nentries:\n  - file: a.md\n    children: []\n",
                "name: V\nentries:\n  - file: ../a.md\n",
                "name: V\nentries:\n  - file: /a.md\n",
                "name: V\nentries:\n  - file: C:/a.md\n",
                "name: V\nentries:\n  - file: dir\\\\a.md\n",
                "name: V\nentries:\n  - file: a//b.md\n",
                "name: V\nentries:\n  - file: a.txt\n",
                "name: V\nentries:\n  - file: a.md\n    visibility: visible\n",
                "name: V\nentries:\n  - file: a.md\n    tags: programming\n");
        for (int i = 0; i < documents.size(); i++) {
            write("invalid" + i + ".yml", documents.get(i));
        }

        ViewStore.LoadResult result = new ViewStore(tmp).load();

        assertThat(result.views()).isEmpty();
        assertThat(result.errors()).hasSize(documents.size());
    }

    @Test
    void duplicateYamlKeysAreRejected() throws Exception {
        write("duplicate.yml", "name: First\nname: Second\nentries: []\n");

        ViewStore.LoadResult result = new ViewStore(tmp).load();

        assertThat(result.views()).isEmpty();
        assertThat(result.errors()).singleElement().asString().contains("duplicate");
    }

    @Test
    void savesUtf8AtomicallyAndRoundTripsOperationsResult() throws Exception {
        Path file = VaultPaths.viewsDir(tmp).resolve("tree.yml");
        ViewEntry shared = ViewEntry.file("notes/a.md", "A", true, List.of("one", "two"));
        ViewEntry branch = ViewEntry.node("Ветка", "Заголовок", false, List.of("tag"),
                new java.util.ArrayList<>(List.of(shared)));
        ViewDefinition original = new ViewDefinition("tree", "Дерево", file,
                new java.util.ArrayList<>(List.of(branch,
                        ViewEntry.node("Клон", null, true, List.of(), new java.util.ArrayList<>(List.of(shared))))));
        ViewStore store = new ViewStore(tmp);

        store.save(original);
        ViewStore.LoadResult loaded = store.load();

        assertThat(loaded.errors()).isEmpty();
        assertThat(Files.readString(file)).doesNotContain("&id", "*id");
        assertThat(loaded.views()).singleElement().satisfies(view -> {
            assertThat(view.name()).isEqualTo("Дерево");
            ViewEntry saved = view.entries().getFirst();
            assertThat(saved.visibility()).isFalse();
            assertThat(saved.overrideTitle()).isEqualTo("Заголовок");
            assertThat(saved.children().getFirst().tags()).containsExactly("one", "two");
        });
        try (var stream = Files.list(VaultPaths.viewsDir(tmp))) {
            assertThat(stream.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".tmp"))).isEmpty();
        }
    }

    @Test
    void createNeverOverwritesExistingView() throws Exception {
        write("tree.yml", "name: Original\nentries: []\n");
        ViewDefinition replacement = new ViewDefinition("tree", "Replacement",
                VaultPaths.viewsDir(tmp).resolve("tree.yml"), new java.util.ArrayList<>());

        assertThatThrownBy(() -> new ViewStore(tmp).create(replacement))
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(VaultPaths.viewsDir(tmp).resolve("tree.yml"))
                .content().isEqualTo("name: Original\nentries: []\n");
    }

    private void write(String name, String content) throws Exception {
        Path file = VaultPaths.viewsDir(tmp).resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
