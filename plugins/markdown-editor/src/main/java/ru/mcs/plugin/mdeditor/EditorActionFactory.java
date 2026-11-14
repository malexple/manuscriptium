package ru.mcs.plugin.mdeditor;

import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import ru.mcs.plugin.mdeditor.action.EditorActionIds;
import ru.mcs.plugin.mdeditor.action.WordCount;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.i18n.I18n;

/** Создаёт действия по запросу declarative ProxyAction из plugin.yml. */
final class EditorActionFactory {

    private final I18n i18n;
    private final Supplier<Optional<MarkdownFileEditor>> selectedEditor;

    EditorActionFactory(I18n i18n, Supplier<Optional<MarkdownFileEditor>> selectedEditor) {
        this.i18n = i18n;
        this.selectedEditor = selectedEditor;
    }

    AnAction create(String id) {
        for (int level = 1; level <= 6; level++) {
            int heading = level;
            if (EditorActionIds.heading(level).equals(id)) {
                return command(id, editor -> EditorCommands.heading(editor, heading));
            }
        }
        return switch (id) {
            case EditorActionIds.TOGGLE_MODE -> modeAction();
            case EditorActionIds.FOCUS_MODE -> toggleAction(id,
                    MarkdownFileEditor::focusMode, (editor, value) -> editor.setFocusMode(value));
            case EditorActionIds.TYPEWRITER_MODE -> toggleAction(id,
                    MarkdownFileEditor::typewriterMode, (editor, value) -> editor.setTypewriterMode(value));
            case EditorActionIds.AUTO_PAIR -> toggleAction(id,
                    MarkdownFileEditor::autoPair, (editor, value) -> editor.setAutoPair(value));
            case EditorActionIds.WORD_COUNT -> command(id, EditorActionFactory.this::showWordCount);
            case EditorActionIds.UNDO -> command(id, editor -> editor.activeCanvas().input().undo());
            case EditorActionIds.REDO -> command(id, editor -> editor.activeCanvas().input().redo());
            case EditorActionIds.CUT -> selectionCommand(id, editor -> editor.activeCanvas().input().cut());
            case EditorActionIds.COPY -> selectionCommand(id, editor -> editor.activeCanvas().input().copy());
            case EditorActionIds.PASTE -> command(id, editor -> editor.activeCanvas().input().paste());
            case EditorActionIds.SELECT_ALL -> command(id, editor -> editor.activeCanvas().input().selectAll());
            case EditorActionIds.DELETE_BLOCK -> command(id, EditorCommands::deleteBlock);
            case EditorActionIds.PARAGRAPH -> command(id, editor -> EditorCommands.heading(editor, 0));
            case EditorActionIds.BOLD -> command(id, editor -> EditorCommands.toggleInline(editor, "**", "**"));
            case EditorActionIds.ITALIC -> command(id, editor -> EditorCommands.toggleInline(editor, "*", "*"));
            case EditorActionIds.UNDERLINE -> command(id, editor -> EditorCommands.toggleInline(editor, "<u>", "</u>"));
            case EditorActionIds.STRIKETHROUGH -> command(id, editor -> EditorCommands.toggleInline(editor, "~~", "~~"));
            case EditorActionIds.INLINE_CODE -> command(id, editor -> EditorCommands.toggleInline(editor, "`", "`"));
            case EditorActionIds.LINK -> command(id, EditorCommands::insertLink);
            case EditorActionIds.CLEAR_FORMATTING -> command(id, EditorCommands::clearFormatting);
            case EditorActionIds.BLOCK_QUOTE -> command(id, EditorCommands::blockQuote);
            case EditorActionIds.BULLET_LIST -> command(id, EditorCommands::bulletList);
            case EditorActionIds.ORDERED_LIST -> command(id, EditorCommands::orderedList);
            case EditorActionIds.TASK_LIST -> command(id, EditorCommands::taskList);
            case EditorActionIds.CODE_BLOCK -> command(id, EditorCommands::codeBlock);
            case EditorActionIds.TABLE -> command(id, EditorCommands::insertTable);
            case EditorActionIds.MATH_BLOCK -> command(id, EditorCommands::insertMathBlock);
            case EditorActionIds.HORIZONTAL_RULE -> command(id, EditorCommands::insertHorizontalRule);
            case EditorActionIds.IMAGE -> command(id, EditorCommands::insertImage);
            default -> null;
        };
    }

    private AnAction modeAction() {
        return new EditorCommand(EditorActionIds.TOGGLE_MODE) {
            @Override
            void perform(MarkdownFileEditor editor) {
                editor.setMode(editor.mode() == EditorMode.SOURCE ? EditorMode.WYSIWYG : EditorMode.SOURCE);
            }

            @Override
            public void update(AnActionEvent event) {
                super.update(event);
                selectedEditor.get().ifPresent(editor -> event.getPresentation().setText(i18n.get(
                        editor.mode() == EditorMode.SOURCE ? "action.toCanvas" : "action.toSource")));
            }
        };
    }

    private AnAction toggleAction(String id, StateGetter getter, StateSetter setter) {
        return new EditorCommand(id) {
            @Override
            void perform(MarkdownFileEditor editor) {
                setter.set(editor, !getter.get(editor));
            }

            @Override
            public void update(AnActionEvent event) {
                super.update(event);
                selectedEditor.get().ifPresent(editor -> event.getPresentation().setText(i18n.get(
                        "action." + shortId(id) + (getter.get(editor) ? ".disable" : ".enable"))));
            }
        };
    }

    private AnAction selectionCommand(String id, EditorOperation operation) {
        return new EditorCommand(id) {
            @Override
            void perform(MarkdownFileEditor editor) {
                operation.perform(editor);
            }

            @Override
            public void update(AnActionEvent event) {
                super.update(event);
                selectedEditor.get().ifPresent(editor -> event.getPresentation().setEnabled(
                        event.getPresentation().isEnabled() && editor.activeCanvas().selectionRange().length() > 0));
            }
        };
    }

    private EditorCommand command(String id, EditorOperation operation) {
        return new EditorCommand(id) {
            @Override
            void perform(MarkdownFileEditor editor) {
                operation.perform(editor);
            }
        };
    }

    private void showWordCount(MarkdownFileEditor editor) {
        WordCount.Stats stats = EditorCommands.wordCount(editor);
        JOptionPane.showMessageDialog(editor.activeCanvas(),
                i18n.get("wordCount.message", stats.words(), stats.characters(), stats.lines(), stats.readingMinutes()),
                i18n.get("wordCount.title"), JOptionPane.INFORMATION_MESSAGE);
        SwingUtilities.invokeLater(() -> editor.activeCanvas().requestFocus());
    }

    private String shortId(String id) {
        return id.substring(EditorActionIds.PREFIX.length());
    }

    @FunctionalInterface
    private interface EditorOperation {
        void perform(MarkdownFileEditor editor);
    }

    @FunctionalInterface
    private interface StateGetter {
        boolean get(MarkdownFileEditor editor);
    }

    @FunctionalInterface
    private interface StateSetter {
        void set(MarkdownFileEditor editor, boolean value);
    }

    private abstract class EditorCommand extends AnAction {

        EditorCommand(String id) {
            super(i18n.get("action." + shortId(id)));
            getTemplatePresentation().setDescription(i18n.get("description." + shortId(id)));
        }

        abstract void perform(MarkdownFileEditor editor);

        @Override
        public final void actionPerformed(AnActionEvent event) {
            selectedEditor.get().filter(MarkdownFileEditor::isLoaded).ifPresent(this::perform);
        }

        @Override
        public void update(AnActionEvent event) {
            boolean available = selectedEditor.get()
                    .map(editor -> editor.isLoaded() && editor.document() != null)
                    .orElse(false);
            event.getPresentation().setEnabledAndVisible(available);
        }
    }
}
