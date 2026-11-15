package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.plugin.mdeditor.layout.BlockBox;
import ru.mcs.plugin.mdeditor.layout.LayoutResult;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.plugin.mdparser.FlexmarkTestAst;
import ru.mcs.spi.editor.ObsidianLinkService;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.AtomicFileStore;

class CitationLayoutTest {

    @TempDir
    Path temp;

    @Test
    void rendersLocalizedVirtualBibliographyWithoutChangingMarkdown() throws Exception {
        Path bib = Files.writeString(temp.resolve("refs.bib"),
                "@article{doe2020, author={Doe, Jane}, title={Paper}, year={2020}}");
        String markdown = "---\nbibliography: refs.bib\n---\n\nCite [@doe2020].\n\nActive";
        Path note = temp.resolve("note.md");
        DocumentModelImpl document = new DocumentModelImpl(markdown,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
        I18n russian = new I18n() {
            @Override
            public Locale getLocale() {
                return Locale.forLanguageTag("ru");
            }

            @Override
            public String get(String key, Object... args) {
                return "bibliography.title".equals(key) ? "Литература" : key;
            }
        };
        WidgetRegistry widgets = new WidgetRegistry(Runnable::run,
                (latex, display, size, color) -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                links(bib), store(), russian);
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, FlexmarkTestAst.parse(markdown), new TestEditorStyleService());
        engine.setWidgets(widgets, temp, note);
        engine.setWidth(800);
        engine.setCaretOffset(markdown.length());

        LayoutResult layout = engine.layout();
        BlockBox bibliography = layout.blocks().getLast();
        StringBuilder visible = new StringBuilder();
        bibliography.lines().forEach(line -> line.runs().forEach(run -> visible.append(run.text())));
        assertThat(visible.toString()).contains("Литература", "Doe, Jane (2020). Paper.");
        assertThat(bibliography.span().start()).isEqualTo(markdown.length());
        assertThat(bibliography.span().end()).isEqualTo(markdown.length());
        assertThat(document.text().toString()).isEqualTo(markdown);
    }

    private static ObsidianLinkService links(Path bib) {
        return new ObsidianLinkService() {
            @Override
            public CompletableFuture<Optional<Target>> resolve(Path source, String target) {
                return CompletableFuture.completedFuture(Optional.of(new Target(bib, null, false)));
            }

            @Override
            public void open(Path source, String target, JComponent anchor) {
            }
        };
    }

    private static AtomicFileStore store() {
        return new AtomicFileStore() {
            @Override
            public byte[] read(Path file) throws IOException {
                return Files.readAllBytes(file);
            }

            @Override
            public void write(Path file, byte[] content) {
            }
        };
    }
}
