package ru.mcs.markdown.notes;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import javax.swing.JComponent;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.editor.EditorDataKeys;
import ru.mcs.spi.editor.EditorTopics;
import ru.mcs.spi.editor.FileEditorListener;

/** Быстрый переход между открытыми заметками и копирование Obsidian-ссылок. */
public final class NotesModule {

    private PluginContext ctx;
    private OpenNotesPanel panel;

    public void start(PluginContext context) {
        this.ctx = context;
        ctx.messageBus().subscribeOnEdt(EditorTopics.EDITOR, new FileEditorListener() {
                    public void fileOpened(Path file) {
                if (panel != null) {
                    panel.refreshFiles();
                }
            }

                    public void fileClosed(Path file) {
                if (panel != null) {
                    panel.refreshFiles();
                }
            }

                    public void activeFileChanged(Path file) {
                if (panel != null) {
                    panel.showActive(file);
                }
            }
        });
    }

    public AnAction createAction(String id) {
        if (!"notes.copyWikiLink".equals(id)) {
            return null;
        }
        return new AnAction(ctx.i18n().get("notes.action.copy"), ctx.i18n().get("notes.action.copy.description"), null) {
                    public void actionPerformed(AnActionEvent event) {
                Path file = event.getData(EditorDataKeys.ACTIVE_FILE);
                if (MarkdownFiles.isMarkdown(file)) {
                    copyWikiLink(file);
                }
            }

                    public void update(AnActionEvent event) {
                event.getPresentation().setEnabled(MarkdownFiles.isMarkdown(event.getData(EditorDataKeys.ACTIVE_FILE)));
            }
        };
    }

    public JComponent createToolWindowContent(String id) {
        if (!"notes.panel".equals(id)) {
            return null;
        }
        if (panel == null) {
            panel = new OpenNotesPanel(ctx.editors(), ctx.i18n(), this::copyWikiLink);
        }
        return panel;
    }

    private void copyWikiLink(Path file) {
        String link = MarkdownFiles.wikiLink(file);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(link), null);
            ctx.statusBar().setMessage(ctx.i18n().get("notes.copied", link));
        } catch (HeadlessException | IllegalStateException e) {
            ctx.notifications().error(ctx.i18n().get("toolwindow.notes"), ctx.i18n().get("notes.copy.error"));
        }
    }
}
