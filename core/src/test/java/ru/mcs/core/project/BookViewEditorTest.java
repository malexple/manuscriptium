package ru.mcs.core.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.service.AtomicFileStoreImpl;

class BookViewEditorTest {

    @TempDir
    Path root;

    @Test
    void chapterEditorsMarkVirtualTabAndSaveOnlyChangedOriginal() throws Exception {
        Path one = Files.writeString(root.resolve("one.md"), "# One\nfirst");
        Path two = Files.writeString(root.resolve("two.md"), "# Two\nsecond");
        ViewDefinition view = new ViewDefinition("book", "Book", root.resolve("book.yml"), List.of(
                ViewEntry.file("one.md", "One", true, List.of()),
                ViewEntry.file("two.md", "Two", true, List.of())));
        Path key = root.resolve(".manuscriptium/books/book.book");
        AtomicReference<BookViewEditor> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new BookViewEditor(
                key, "Book", root, view, List.of(), new AtomicFileStoreImpl())));
        BookViewEditor editor = reference.get();
        try {
            List<JTextArea> chapters = awaitChapters(editor, 2);
            assertThat(chapters.get(0).getText()).isEqualTo("# One\nfirst");
            assertThat(chapters.get(1).getText()).isEqualTo("# Two\nsecond");
            assertThat(editor.getFile()).isEqualTo(key);
            assertThat(editor.isModified()).isFalse();

            AtomicInteger notifications = new AtomicInteger();
            AtomicReference<CompletableFuture<Void>> saving = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                editor.addModifiedListener(notifications::incrementAndGet);
                chapters.get(0).setText("# One\nedited");
                saving.set(editor.saveAsync());
            });
            assertThat(editor.isModified()).isTrue();
            saving.get().get(5, TimeUnit.SECONDS);

            assertThat(Files.readString(one)).isEqualTo("# One\nedited");
            assertThat(Files.readString(two)).isEqualTo("# Two\nsecond");
            assertThat(editor.isModified()).isFalse();
            assertThat(notifications).hasValue(2);
            SwingUtilities.invokeAndWait(() -> assertThat(button(editor, Messages.get("book.save")).isEnabled()).isFalse());
        } finally {
            SwingUtilities.invokeAndWait(editor::dispose);
        }
    }

    private static List<JTextArea> awaitChapters(BookViewEditor editor, int expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        AtomicReference<List<JTextArea>> result = new AtomicReference<>(List.of());
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                List<JTextArea> areas = new ArrayList<>();
                collect(editor.getComponent(), areas);
                result.set(areas);
            });
            if (result.get().size() == expected) {
                return result.get();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Book chapters were not loaded before timeout");
    }

    private static void collect(Component component, List<JTextArea> result) {
        if (component instanceof JTextArea area) {
            result.add(area);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, result);
            }
        }
    }

    private static JButton button(BookViewEditor editor, String text) {
        List<JButton> buttons = new ArrayList<>();
        collectButtons(editor.getComponent(), buttons);
        return buttons.stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static void collectButtons(Component component, List<JButton> result) {
        if (component instanceof JButton button) {
            result.add(button);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtons(child, result);
            }
        }
    }
}
