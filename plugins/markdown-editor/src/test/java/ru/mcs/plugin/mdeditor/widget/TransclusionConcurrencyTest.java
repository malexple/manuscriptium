package ru.mcs.plugin.mdeditor.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.swing.editor.ObsidianLinkService;
import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.service.AtomicFileStore;

class TransclusionConcurrencyTest {

    @TempDir
    Path temp;

    @Test
    void sectionSaveMergesIntoLatestFileAfterFullTransclusionSave() throws Exception {
        Path parent = Files.writeString(temp.resolve("book.md"), "![[Chapter]]\n![[Chapter#Details]]");
        Path chapter = Files.writeString(temp.resolve("Chapter.md"),
                "# Chapter\n\nIntro original.\n\n## Details\n\nDetail original.\n\n## Next\n\nNext stays.\n");
        AtomicFileStore store = new AtomicFileStore() {
            @Override
            public byte[] read(Path file) throws java.io.IOException {
                return Files.readAllBytes(file);
            }

            @Override
            public void write(Path file, byte[] content) throws java.io.IOException {
                Files.write(file, content);
            }
        };
        ObsidianLinkService links = new ObsidianLinkService() {
            @Override
            public CompletableFuture<Optional<Target>> resolve(Path source, String target) {
                int hash = target.indexOf('#');
                String fragment = hash < 0 ? null : target.substring(hash + 1);
                return CompletableFuture.completedFuture(Optional.of(new Target(chapter, fragment, true)));
            }

            @Override
            public void open(Path source, String target, JComponent anchor) {
            }
        };
        WidgetRegistry registry = new WidgetRegistry(Runnable::run, null, links, store);
        TransclusionWidget full = create(registry, parent, "![[Chapter]]");
        TransclusionWidget section = create(registry, parent, "![[Chapter#Details]]");
        SwingUtilities.invokeAndWait(() -> { });
        JTextArea fullEditor = find(full.component(), JTextArea.class);
        JTextArea sectionEditor = find(section.component(), JTextArea.class);

        SwingUtilities.invokeAndWait(() -> fullEditor.setText(
                "# Chapter\n\nIntro edited by full embed.\n\n## Details\n\nDetail original.\n\n## Next\n\nNext stays.\n"));
        SwingUtilities.invokeAndWait(findButton(full.component(), "Save")::doClick);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> sectionEditor.setText("## Details\n\nDetail edited by section embed.\n\n"));
        SwingUtilities.invokeAndWait(findButton(section.component(), "Save")::doClick);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(Files.readString(chapter)).isEqualTo(
                "# Chapter\n\nIntro edited by full embed.\n\n## Details\n\nDetail edited by section embed.\n\n## Next\n\nNext stays.\n");
        assertThat(Files.readString(parent)).isEqualTo("![[Chapter]]\n![[Chapter#Details]]");
    }

    private TransclusionWidget create(WidgetRegistry registry, Path parent, String embed) throws Exception {
        AtomicReference<TransclusionWidget> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> created.set((TransclusionWidget) registry.create(embed,
                new BlockSpan(BlockKind.EMBED, 0, embed.length(), 1), embed.length(), 500, false,
                ComputedStyle.defaults(), new Font(Font.SANS_SERIF, Font.PLAIN, 14), temp, parent)));
        return created.get();
    }

    private static JButton findButton(Container root, String text) {
        return buttons(root).stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static List<JButton> buttons(Container root) {
        List<JButton> result = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button) {
                result.add(button);
            }
            if (component instanceof Container container) {
                result.addAll(buttons(container));
            }
        }
        return result;
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        for (Component component : root.getComponents()) {
            if (component instanceof Container container) {
                try {
                    return find(container, type);
                } catch (IllegalStateException ignored) {
                    // Продолжаем поиск в остальных ветвях.
                }
            }
        }
        throw new IllegalStateException("Component not found: " + type);
    }
}
