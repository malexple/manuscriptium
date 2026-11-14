package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.spi.i18n.I18n;

class EditorCommandsTest {

    @TempDir
    Path temp;

    @Test
    void inlineFormattingHeadingsAndQuotesRoundTripThroughDocumentModel() throws Exception {
        MarkdownFileEditor editor = create("Body");
        try {
            SwingUtilities.invokeAndWait(() -> {
                editor.source().setCaretSelection(0, 4);
                EditorCommands.toggleInline(editor, "**", "**");
                assertThat(editor.sourceText()).isEqualTo("**Body**");
                assertThat(editor.source().selectionRange().start()).isEqualTo(2);
                assertThat(editor.source().selectionRange().end()).isEqualTo(6);

                EditorCommands.toggleInline(editor, "**", "**");
                assertThat(editor.sourceText()).isEqualTo("Body");
                EditorCommands.heading(editor, 2);
                assertThat(editor.sourceText()).isEqualTo("## Body");
                EditorCommands.heading(editor, 0);
                assertThat(editor.sourceText()).isEqualTo("Body");
                EditorCommands.blockQuote(editor);
                assertThat(editor.sourceText()).isEqualTo("> Body");
                EditorCommands.blockQuote(editor);
                assertThat(editor.sourceText()).isEqualTo("Body");
            });
        } finally {
            dispose(editor);
        }
    }

    @Test
    void orderedListAndCodeBlockToggleOnlyTheirSourceRange() throws Exception {
        MarkdownFileEditor editor = create("one\ntwo");
        try {
            SwingUtilities.invokeAndWait(() -> {
                editor.source().input().selectAll();
                EditorCommands.orderedList(editor);
                assertThat(editor.sourceText()).isEqualTo("1. one\n2. two");
                EditorCommands.orderedList(editor);
                assertThat(editor.sourceText()).isEqualTo("one\ntwo");

                editor.source().setCaretSelection(0, 3);
                EditorCommands.codeBlock(editor);
                assertThat(editor.sourceText()).isEqualTo("```\none\n```\ntwo");
                EditorCommands.codeBlock(editor);
                assertThat(editor.sourceText()).isEqualTo("one\ntwo");
            });
        } finally {
            dispose(editor);
        }
    }

    @Test
    void modeSwitchPreservesSelectionAndModesApplyToBothCanvases() throws Exception {
        MarkdownFileEditor editor = create("Body");
        try {
            SwingUtilities.invokeAndWait(() -> {
                editor.source().setCaretSelection(1, 3);
                editor.setMode(EditorMode.WYSIWYG);
                assertThat(editor.canvas().selectionRange().start()).isEqualTo(1);
                assertThat(editor.canvas().selectionRange().end()).isEqualTo(3);

                editor.setFocusMode(true);
                editor.setTypewriterMode(true);
                editor.setAutoPair(false);
                assertThat(editor.focusMode()).isTrue();
                assertThat(editor.canvas().typewriterMode()).isTrue();
                assertThat(editor.source().typewriterMode()).isTrue();
                assertThat(editor.canvas().autoPairEnabled()).isFalse();
                assertThat(editor.source().autoPairEnabled()).isFalse();

                editor.setMode(EditorMode.SOURCE);
                assertThat(editor.source().selectionRange().start()).isEqualTo(1);
                assertThat(editor.source().selectionRange().end()).isEqualTo(3);
            });
        } finally {
            dispose(editor);
        }
    }

    @Test
    void insertAndDeleteCommandsUseSharedSource() throws Exception {
        MarkdownFileEditor editor = create("Body");
        try {
            SwingUtilities.invokeAndWait(() -> {
                editor.source().setCaretOffset(0);
                EditorCommands.insertTable(editor);
                assertThat(editor.sourceText()).startsWith("| Left | Right |\n|:-----|------:|");
                editor.document().undo().undo();
                EditorCommands.insertMathBlock(editor);
                assertThat(editor.sourceText()).startsWith("$$\n\n$$");
                editor.document().undo().undo();
                EditorCommands.insertImage(editor);
                assertThat(editor.sourceText()).startsWith("![alt text](image.png)");
                editor.document().undo().undo();

                EditorCommands.deleteBlock(editor);
                assertThat(editor.sourceText()).isEmpty();
                assertThat(WordCountSelection.stats(editor).words()).isZero();
            });
        } finally {
            dispose(editor);
        }
    }

    private MarkdownFileEditor create(String text) throws Exception {
        Path file = Files.writeString(temp.resolve("commands.md"), text);
        AtomicReference<MarkdownFileEditor> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(new MarkdownFileEditor(file, EditorMode.SOURCE,
                new MarkdownEditorRegistry(), I18n.passthrough(), new TestAtomicFileStore(),
                new TestMarkdownParser(), TestMarkdownParser.EXECUTOR)));
        MarkdownFileEditor editor = result.get();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!editor.isLoaded() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(editor.isLoaded()).isTrue();
        SwingUtilities.invokeAndWait(() -> { });
        return editor;
    }

    private static void dispose(MarkdownFileEditor editor) throws Exception {
        SwingUtilities.invokeAndWait(editor::dispose);
    }

    /** Изолирует тестовый вызов от UI-диалога действия wordCount. */
    private static final class WordCountSelection {
        static ru.mcs.plugin.mdeditor.action.WordCount.Stats stats(MarkdownFileEditor editor) {
            return EditorCommands.wordCount(editor);
        }
    }
}
