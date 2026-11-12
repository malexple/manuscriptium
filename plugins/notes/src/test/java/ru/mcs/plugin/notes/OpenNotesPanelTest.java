package ru.mcs.plugin.notes;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorManager;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.i18n.I18n;

class OpenNotesPanelTest {

    @TempDir
    Path temp;

    @Test
    void filtersMarkdownSelectsNotesCopiesLinksAndLoadsStatistics() throws Exception {
        Path first = Files.writeString(temp.resolve("First.md"), "one two");
        Path second = Files.writeString(temp.resolve("Second.md"), "x");
        Path text = Files.writeString(temp.resolve("ignored.txt"), "not shown");
        FakeEditors editors = new FakeEditors(List.of(first, text, second), first);
        AtomicReference<Path> copied = new AtomicReference<>();
        AtomicReference<OpenNotesPanel> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> created.set(new OpenNotesPanel(editors, new TestMessages(), copied::set)));
        OpenNotesPanel panel = created.get();

        JList<?> list = component(panel, "notes.list", JList.class);
        assertThat(list.getModel().getSize()).isEqualTo(2);
        assertThat(list.getModel().getElementAt(0)).isEqualTo(first);
        assertThat(list.getModel().getElementAt(1)).isEqualTo(second);
        awaitStatistics(panel, "2 words · 7 characters · 1 lines");

        SwingUtilities.invokeAndWait(() -> {
            @SuppressWarnings("unchecked")
            JList<Path> notes = (JList<Path>) list;
            notes.setSelectedValue(second, true);
        });
        flushEdt();
        component(panel, "notes.open", JButton.class).doClick();
        flushEdt();
        assertThat(editors.selected).isEqualTo(second);
        awaitStatistics(panel, "1 words · 1 characters · 1 lines");

        component(panel, "notes.copy", JButton.class).doClick();
        assertThat(copied).hasValue(second);
        assertThat(MarkdownFiles.wikiLink(copied.get())).isEqualTo("[[Second]]");
    }

    private static void awaitStatistics(OpenNotesPanel panel, String expected) throws Exception {
        JLabel label = component(panel, "notes.stats", JLabel.class);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            flushEdt();
            if (expected.equals(label.getText())) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(label.getText()).isEqualTo(expected);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static <T> T component(Container root, String name, Class<T> type) {
        T result = findComponent(root, name, type);
        if (result == null) {
            throw new AssertionError("Component not found: " + name);
        }
        return result;
    }

    private static <T> T findComponent(Container root, String name, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = findComponent(container, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class TestMessages implements I18n {
        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public String get(String key, Object... args) {
            if ("notes.stats".equals(key)) {
                return args[0] + " words · " + args[1] + " characters · " + args[2] + " lines";
            }
            return key;
        }
    }

    private static final class FakeEditors implements FileEditorManager {
        private final List<Path> open = new ArrayList<>();
        private Path selected;

        private FakeEditors(List<Path> files, Path selected) {
            open.addAll(files);
            this.selected = selected;
        }

        @Override public void registerProvider(FileEditorProvider provider) { }
        @Override public void unregisterProvider(FileEditorProvider provider) { }
        @Override public boolean openFile(Path file) { if (!open.contains(file)) open.add(file); selected = file; return true; }
        @Override public boolean closeFile(Path file) { open.remove(file); if (file.equals(selected)) selected = null; return true; }
        @Override public List<Path> getOpenFiles() { return List.copyOf(open); }
        @Override public Optional<Path> getSelectedFile() { return Optional.ofNullable(selected); }
        @Override public void selectFile(Path file) { if (open.contains(file)) selected = file; }
        @Override public boolean isPinned(Path file) { return false; }
        @Override public void setPinned(Path file, boolean pinned) { }
        @Override public void saveAll() { }
    }
}
