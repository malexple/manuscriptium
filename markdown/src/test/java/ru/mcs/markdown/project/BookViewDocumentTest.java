package ru.mcs.markdown.project;

import ru.mcs.spi.project.ViewEntry;
import ru.mcs.spi.project.ViewDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.service.AtomicFileStore;

class BookViewDocumentTest {

    @TempDir
    Path root;

    @Test
    void loadsVisibleSubtreeEditsOriginalChapterAndExportsWithoutOverwrite() throws Exception {
        Path one = Files.writeString(root.resolve("one.md"), "# One\nfirst\n");
        Path hidden = Files.writeString(root.resolve("hidden.md"), "# Hidden\n");
        Path two = Files.writeString(root.resolve("two.md"), "# Two\nsecond\n");
        ViewDefinition view = new ViewDefinition("book", "Book", root.resolve(".manuscriptium/views/book.yml"),
                List.of(ViewEntry.node("Part", null, true, List.of(), List.of(
                        ViewEntry.file("one.md", null, true, List.of()),
                        ViewEntry.file("hidden.md", null, false, List.of()))),
                        ViewEntry.file("two.md", null, true, List.of())));
        AtomicFileStore store = new TestAtomicFileStore();

        BookViewDocument book = BookViewDocument.load(root, view, List.of(), store);
        assertThat(book.chapters()).extracting(BookViewDocument.Chapter::file).containsExactly(one, two);
        assertThat(book.markdown(Map.of())).isEqualTo("# One\nfirst\n\n# Two\nsecond\n");

        BookViewDocument part = BookViewDocument.load(root, view, List.of(0), store);
        assertThat(part.chapters()).extracting(BookViewDocument.Chapter::file).containsExactly(one);
        part.save(Map.of(one, "# One\nedited\n"), store);
        assertThat(Files.readString(one)).isEqualTo("# One\nedited\n");
        assertThat(Files.readString(two)).isEqualTo("# Two\nsecond\n");
        assertThat(Files.readString(hidden)).isEqualTo("# Hidden\n");

        Path exported = root.resolve("part.md");
        part.export(exported, Map.of(one, "# One\nedited\n"));
        assertThat(Files.readString(exported, StandardCharsets.UTF_8)).isEqualTo("# One\nedited\n");
        assertThatThrownBy(() -> part.export(exported, Map.of()))
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readString(exported)).isEqualTo("# One\nedited\n");
    }

    @Test
    void recordsOnlyWritesForChangedFiles() throws Exception {
        Path one = Files.writeString(root.resolve("one.md"), "one");
        Path two = Files.writeString(root.resolve("two.md"), "two");
        ViewDefinition view = new ViewDefinition("book", "Book", root.resolve("book.yml"),
                List.of(ViewEntry.file("one.md", null, true, List.of()),
                        ViewEntry.file("two.md", null, true, List.of())));
        BookViewDocument book = BookViewDocument.load(root, view, List.of(), new TestAtomicFileStore());
        List<Path> writes = new ArrayList<>();
        AtomicFileStore recording = new AtomicFileStore() {
            @Override
            public byte[] read(Path file) throws IOException {
                return Files.readAllBytes(file);
            }

            @Override
            public void write(Path file, byte[] content) {
                writes.add(file);
            }
        };

        book.save(Map.of(one, "one", two, "changed"), recording);

        assertThat(writes).containsExactly(two);
    }
}
