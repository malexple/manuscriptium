package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.vault.VaultNote;

class ObsidianLinkNavigatorTest {

    @TempDir
    Path root;

    @Test
    void opensNoteSelectsFragmentAndReportsMissingTarget() throws Exception {
        Messages.init(java.util.Locale.ENGLISH);
        Path source = Files.writeString(root.resolve("source.md"), "[[Target#Second title|alias]]");
        Path target = Files.writeString(root.resolve("Target.md"), "# First\n## Second title\nbody\n");
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        ObsidianLinkNavigator navigator = new ObsidianLinkNavigator(editors);
        editors.registerProvider(new TextFileEditorProvider(navigator));
        AtomicReference<String> status = new AtomicReference<>();
        navigator.setStatusHandler(status::set);
        navigator.setVault(root, List.of(note(source), note(target)));

        SwingUtilities.invokeAndWait(() -> navigator.open(source,
                new ObsidianSyntax.Link(0, 10, false, "Target", "Second title", "alias"), new JPanel()));
        await(() -> editors.getSelectedFile().filter(target::equals).isPresent());
        SwingUtilities.invokeAndWait(() -> { });

        TextFileEditor editor = (TextFileEditor) editors.selectedEditor().orElseThrow();
        JTextArea area = (JTextArea) ((JScrollPane) editor.getComponent()).getViewport().getView();
        assertThat(area.getSelectedText()).isEqualTo("## Second title");
        assertThat(status.get()).contains("Target.md");

        SwingUtilities.invokeAndWait(() -> navigator.open(source,
                new ObsidianSyntax.Link(0, 10, false, "Missing", null, null), new JPanel()));
        await(() -> status.get() != null && status.get().contains("Missing"));
        assertThat(editors.getSelectedFile()).contains(target);
    }

    private static VaultNote note(Path file) {
        String name = file.getFileName().toString();
        return new VaultNote(file, name, name.substring(0, name.length() - 3));
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
            SwingUtilities.invokeAndWait(() -> { });
        }
        assertThat(condition.getAsBoolean()).as("asynchronous navigation before timeout").isTrue();
    }
}
