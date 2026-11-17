package ru.mcs.markdown.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.swing.editor.ObsidianLinkService;
import ru.mcs.spi.service.AtomicFileStore;

class CitationIndexTest {

    private static final String BIB = """
            @article{doe2020,
              author = {Doe, Jane and Smith, John},
              title = {A {Great} Paper},
              year = {2020}
            }
            @book{smith2019,
              author = {Smith, John},
              title = {Book Title},
              date = {2019-04-01}
            }
            """;

    @Test
    void rendersSingleGroupedLocatedCitationsAndCompletion() {
        CitationIndex index = CitationIndex.of(BIB, null);

        assertThat(index.citation("[@doe2020]")).isEqualTo("(Doe et al., 2020)");
        assertThat(index.citation("[@doe2020; @smith2019]"))
                .isEqualTo("(Doe et al., 2020; Smith, 2019)");
        assertThat(index.citation("[@doe2020, p. 12]")).isEqualTo("(Doe et al., 2020, p. 12)");
        assertThat(index.citation("[@missing]")).isNull();
        assertThat(index.complete("do")).containsExactly("doe2020");
        assertThat(index.complete("")).containsExactly("doe2020", "smith2019");
    }

    @Test
    void readsCslLayoutAndBuildsBibliographyFromCitedKeysOnly() {
        String csl = "<style><citation><layout prefix=\"[\" suffix=\"]\" delimiter=\" | \">"
                + "<names variable=\"author\"/><date variable=\"issued\"/></layout></citation></style>";
        CitationIndex index = CitationIndex.of(BIB, csl);

        assertThat(index.citation("[@doe2020; @smith2019]")).isEqualTo("[Doe et al., 2020 | Smith, 2019]");
        assertThat(index.bibliography("Cite [@smith2019]. Again [@doe2020].")).containsExactly(
                "Smith, John (2019). Book Title.", "Doe, Jane, Smith, John (2020). A Great Paper.");
    }

    @Test
    void readsFrontMatterConfigurationAndLoadsBibliographyAsynchronously(@TempDir Path temp) throws Exception {
        Path note = Files.writeString(temp.resolve("note.md"), "note");
        Path bib = Files.writeString(temp.resolve("refs.bib"), BIB);
        String markdown = "---\r\nbibliography: 'refs.bib'\r\n---\r\nCite [@doe2020].";
        CitationIndex.Configuration configuration = CitationIndex.configuration(markdown);
        ObsidianLinkService links = new ObsidianLinkService() {
            @Override
            public CompletableFuture<Optional<Target>> resolve(Path source, String target) {
                return CompletableFuture.completedFuture(Optional.of(new Target(bib, null, false)));
            }

            @Override
            public void open(Path source, String target, JComponent anchor) {
            }
        };
        AtomicFileStore store = new AtomicFileStore() {
            @Override
            public byte[] read(Path file) throws java.io.IOException {
                return Files.readAllBytes(file);
            }

            @Override
            public void write(Path file, byte[] content) {
            }
        };

        CitationIndex loaded = CitationIndex.load(configuration, note, links, store, Runnable::run).join();

        assertThat(configuration.bibliography()).isEqualTo("refs.bib");
        assertThat(loaded.citation("[@doe2020]")).isEqualTo("(Doe et al., 2020)");
    }
}
