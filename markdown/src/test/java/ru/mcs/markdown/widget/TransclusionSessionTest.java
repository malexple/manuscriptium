package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
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
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;
import ru.mcs.markdown.api.ObsidianLinkService;
import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.service.AtomicFileStore;

class TransclusionSessionTest {

    @TempDir
    Path temp;

    @Test
    void editsSectionInPlaceAndWritesOnlyOriginalChapter() throws Exception {
        Path parent = Files.writeString(temp.resolve("book.md"), "![[Chapter#Part]]");
        Path chapter = Files.writeString(temp.resolve("Chapter.md"),
                "# Chapter\nbefore\n## Part\nold\n### Child\nkeep\n## Next\nafter\n");
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
                return CompletableFuture.completedFuture(Optional.of(new Target(chapter, "Part", true)));
            }

            @Override
            public void open(Path source, String target, JComponent anchor) {
            }
        };
        WidgetRegistry registry = new WidgetRegistry(Runnable::run, null, links, store);
        AtomicReference<TransclusionWidget> created = new AtomicReference<>();
        String embed = "![[Chapter#Part]]";
        SwingUtilities.invokeAndWait(() -> created.set((TransclusionWidget) registry.create(embed,
                new BlockSpan(BlockKind.EMBED, 0, embed.length(), 1), embed.length(), 500, false,
                ComputedStyle.defaults(), new Font(Font.SANS_SERIF, Font.PLAIN, 14), temp, parent)));
        SwingUtilities.invokeAndWait(() -> { });

        TransclusionWidget widget = created.get();
        assertThat(widget).isNotNull();
        JTextArea editor = find(widget.component(), JTextArea.class);
        assertThat(editor.getText()).isEqualTo("## Part\nold\n### Child\nkeep\n");
        SwingUtilities.invokeAndWait(() -> editor.setText("## Part\nchanged\n### Child\nkeep\n"));
        JButton save = findButton(widget.component(), "Save");
        SwingUtilities.invokeAndWait(save::doClick);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(Files.readString(chapter, StandardCharsets.UTF_8)).isEqualTo(
                "# Chapter\nbefore\n## Part\nchanged\n### Child\nkeep\n## Next\nafter\n");
        assertThat(Files.readString(parent)).isEqualTo(embed);

        AtomicReference<TransclusionWidget> rebuilt = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> rebuilt.set((TransclusionWidget) registry.create(embed,
                new BlockSpan(BlockKind.EMBED, 0, embed.length(), 1), embed.length(), 500, false,
                ComputedStyle.defaults(), new Font(Font.SANS_SERIF, Font.PLAIN, 14), temp, parent)));
        assertThat(rebuilt.get().component()).isSameAs(widget.component());
    }

    @Test
    void duplicateEmbedsAtDifferentOffsetsHaveIndependentComponents() throws Exception {
        String embed = "![[Chapter#Part]]";
        String markdown = embed + "\n\n" + embed;
        Path parent = temp.resolve("book.md");
        WidgetRegistry registry = new WidgetRegistry(Runnable::run, null);
        AtomicReference<TransclusionWidget> first = new AtomicReference<>();
        AtomicReference<TransclusionWidget> second = new AtomicReference<>();
        AtomicReference<TransclusionWidget> rebuiltFirst = new AtomicReference<>();
        int secondStart = embed.length() + 2;

        SwingUtilities.invokeAndWait(() -> {
            first.set((TransclusionWidget) registry.create(markdown,
                    new BlockSpan(BlockKind.EMBED, 0, embed.length(), 1), embed.length(), 500, false,
                    ComputedStyle.defaults(), new Font(Font.SANS_SERIF, Font.PLAIN, 14), temp, parent));
            second.set((TransclusionWidget) registry.create(markdown,
                    new BlockSpan(BlockKind.EMBED, secondStart, markdown.length(), 3), markdown.length(), 500, false,
                    ComputedStyle.defaults(), new Font(Font.SANS_SERIF, Font.PLAIN, 14), temp, parent));
            rebuiltFirst.set((TransclusionWidget) registry.create(markdown,
                    new BlockSpan(BlockKind.EMBED, 0, embed.length(), 1), embed.length(), 500, false,
                    ComputedStyle.defaults(), new Font(Font.SANS_SERIF, Font.PLAIN, 14), temp, parent));
        });

        assertThat(first.get()).isNotNull();
        assertThat(second.get()).isNotNull();
        assertThat(first.get().component()).isNotSameAs(second.get().component());
        assertThat(rebuiltFirst.get().component()).isSameAs(first.get().component());
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
            if (type.isInstance(component)) {
                return type.cast(component);
            }
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
