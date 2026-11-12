package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObsidianEditorSupportTest {

    @TempDir
    Path tmp;

    @Test
    void highlightsAndNavigatesOnlyMarkdownAndRefreshesAfterEdits() throws Exception {
        Path file = Files.writeString(tmp.resolve("note.md"),
                "[[Target]]\n![[image.png]]\n> [!tip] Useful\n> body\n");
        AtomicReference<ObsidianSyntax.Link> opened = new AtomicReference<>();
        TextFileEditorProvider provider = new TextFileEditorProvider((source, link, anchor) -> opened.set(link));
        TextFileEditor editor = (TextFileEditor) provider.createEditor(file);
        flushEdt();

        assertThat(editor.obsidian().result().links()).hasSize(2);
        assertThat(editor.obsidian().result().callouts()).hasSize(1);
        assertThat(editor.text()).contains("[[Target]]");
        int targetOffset = editor.text().indexOf("[[Target]]") + 2;
        assertThat(editor.obsidian().openAt(targetOffset, new JPanel())).isTrue();
        assertThat(opened.get().target()).isEqualTo("Target");
        assertThat(editor.obsidian().openAt(0, new JPanel())).isTrue();
        assertThat(editor.isModified()).isFalse();

        SwingUtilities.invokeAndWait(() -> editor.setText("```\n[[ignored]]\n```\n[[changed]]"));
        flushEdt();
        assertThat(editor.obsidian().result().links()).singleElement()
                .satisfies(link -> assertThat(link.target()).isEqualTo("changed"));

        Path plain = Files.writeString(tmp.resolve("plain.txt"), "[[Target]]");
        TextFileEditor plainEditor = (TextFileEditor) provider.createEditor(plain);
        assertThat(plainEditor.obsidian()).isNull();
    }

    @Test
    void selectsHeadingAndBlockFragmentWithoutChangingText() throws Exception {
        Path file = Files.writeString(tmp.resolve("fragments.md"), """
                # First
                body
                ## Second title ##
                paragraph ^block-7
                """);
        TextFileEditor editor = (TextFileEditor) new TextFileEditorProvider().createEditor(file);

        SwingUtilities.invokeAndWait(() -> editor.selectFragment("Second title"));
        assertThat(selectedText(editor)).isEqualTo("## Second title ##");

        SwingUtilities.invokeAndWait(() -> editor.selectFragment("^block-7"));
        assertThat(selectedText(editor)).isEqualTo("paragraph ^block-7");
        assertThat(editor.isModified()).isFalse();
    }

    private static String selectedText(TextFileEditor editor) throws Exception {
        AtomicReference<String> selection = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) editor.getComponent();
            javax.swing.JTextArea area = (javax.swing.JTextArea) scroll.getViewport().getView();
            selection.set(area.getSelectedText());
        });
        return selection.get();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }
}
