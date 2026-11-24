package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.markdown.editor.mode.EditorMode;
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
                EditorCommands.insertTable(editor, 2, 3);
                assertThat(editor.sourceText()).startsWith("|  |  |\n| :--- | :--- |\n|  |  |\n|  |  |\n\nBody");
                assertThat(editor.source().caretOffset()).isEqualTo(2);
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

    @Test
    void toggleCaseUsesSelectionOrCurrentUnicodeWord() throws Exception {
        MarkdownFileEditor editor = create("One two\nпривет");
        try {
            SwingUtilities.invokeAndWait(() -> {
                editor.source().setCaretSelection(0, 3);
                EditorCommands.toggleCase(editor);
                assertThat(editor.sourceText()).isEqualTo("ONE two\nпривет");
                assertThat(editor.source().selectionRange().start()).isZero();
                assertThat(editor.source().selectionRange().end()).isEqualTo(3);

                EditorCommands.toggleCase(editor);
                assertThat(editor.sourceText()).isEqualTo("one two\nпривет");

                editor.source().setCaretSelection(9, 9);
                EditorCommands.toggleCase(editor);
                assertThat(editor.sourceText()).isEqualTo("one two\nПРИВЕТ");
                assertThat(editor.source().selectionRange().start()).isEqualTo(8);
                assertThat(editor.source().selectionRange().end()).isEqualTo(14);
            });
        } finally {
            dispose(editor);
        }
    }

    @Test
    void duplicateAndDeleteLineHandleMiddleLastAndOnlyLines() throws Exception {
        MarkdownFileEditor editor = create("one\ntwo");
        try {
            SwingUtilities.invokeAndWait(() -> {
                editor.source().setCaretSelection(1, 1);
                EditorCommands.duplicateLine(editor);
                assertThat(editor.sourceText()).isEqualTo("one\none\ntwo");
                assertThat(editor.source().caretOffset()).isEqualTo(5);

                editor.source().setCaretSelection(editor.document().length(), editor.document().length());
                EditorCommands.duplicateLine(editor);
                assertThat(editor.sourceText()).isEqualTo("one\none\ntwo\ntwo");
                assertThat(editor.source().caretOffset()).isEqualTo(editor.document().length());

                editor.source().setCaretSelection(1, 1);
                EditorCommands.deleteLine(editor);
                assertThat(editor.sourceText()).isEqualTo("one\ntwo\ntwo");
                assertThat(editor.source().caretOffset()).isZero();

                editor.source().setCaretSelection(editor.document().length(), editor.document().length());
                EditorCommands.deleteLine(editor);
                assertThat(editor.sourceText()).isEqualTo("one\ntwo");
                assertThat(editor.source().caretOffset()).isEqualTo(7);

                editor.source().setCaretSelection(5, 5);
                EditorCommands.deleteLine(editor);
                assertThat(editor.sourceText()).isEqualTo("one");
                assertThat(editor.source().caretOffset()).isEqualTo(3);

                EditorCommands.deleteLine(editor);
                assertThat(editor.sourceText()).isEmpty();
                assertThat(editor.source().caretOffset()).isZero();
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
        static ru.mcs.markdown.editor.action.WordCount.Stats stats(MarkdownFileEditor editor) {
            return EditorCommands.wordCount(editor);
        }
    }
}
