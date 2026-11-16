package ru.mcs.plugin.mdeditor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.datatransfer.Clipboard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.spi.swing.editor.ObsidianLinkService;
import ru.mcs.spi.service.AtomicFileStore;

class EditorCanvasCitationTest {

    @TempDir
    Path temp;

    @Test
    void completionReplacesOnlyCurrentCitationAndSupportsUndoRedo() throws Exception {
        Path bib = Files.writeString(temp.resolve("refs.bib"), """
                @article{doe2020, author={Doe, Jane}, title={Paper}, year={2020}}
                @book{smith2019, author={Smith, John}, title={Book}, year={2019}}
                """);
        Path note = temp.resolve("note.md");
        String markdown = "---\nbibliography: refs.bib\n---\nCite [@do";
        EditorCanvas canvas = canvas(markdown);
        WidgetRegistry widgets = new WidgetRegistry(Runnable::run, null, links(bib), store());
        try {
            AtomicReference<JPopupMenu> popup = new AtomicReference<>();
            runEdt(() -> canvas.setWidgets(widgets, temp, note));
            assertThat(widgets.citations(markdown, note).complete("do")).containsExactly("doe2020");
            runEdt(() -> {
                canvas.setCaretOffset(markdown.length());
                popup.set(canvas.createCitationCompletion());
            });
            assertThat(canvas.caretOffset()).isEqualTo(markdown.length());
            assertThat(canvas.caretOffset()).isEqualTo(canvas.selectionAnchor());
            int at = markdown.lastIndexOf("@", Math.max(0, canvas.caretOffset() - 1));
            assertThat(at).isPositive();
            assertThat(markdown.charAt(at - 1)).isEqualTo('[');
            assertThat(markdown.substring(at + 1, canvas.caretOffset())).isEqualTo("do");
            assertThat(popup.get()).isNotNull();
            assertThat(popup.get().getComponentCount()).isEqualTo(1);
            assertThat(((JMenuItem) popup.get().getComponent(0)).getText()).isEqualTo("doe2020");

            runEdt(() -> ((JMenuItem) popup.get().getComponent(0)).doClick());
            assertThat(canvas.document().text().toString()).isEqualTo(markdown + "e2020");
            assertThat(canvas.caretOffset()).isEqualTo(markdown.length() + 5);

            runEdt(() -> canvas.document().undo().undo());
            assertThat(canvas.document().text().toString()).isEqualTo(markdown);
            runEdt(() -> canvas.document().undo().redo());
            assertThat(canvas.document().text().toString()).isEqualTo(markdown + "e2020");
        } finally {
            runEdt(canvas::dispose);
        }
    }

    private EditorCanvas canvas(String text) throws Exception {
        AtomicReference<EditorCanvas> result = new AtomicReference<>();
        runEdt(() -> {
            EditorCanvas canvas = new EditorCanvas(new ClipboardService(new Clipboard("citations")));
            canvas.setSize(600, 300);
            canvas.setDocument(new DocumentModelImpl(text,
                    new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false)));
            result.set(canvas);
        });
        return result.get();
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

    private static void runEdt(RunnableWithException action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                action.run();
            } catch (Throwable e) {
                error.set(e);
            }
        });
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    @FunctionalInterface
    private interface RunnableWithException {
        void run() throws Exception;
    }
}
