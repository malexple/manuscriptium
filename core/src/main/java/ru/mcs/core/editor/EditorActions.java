package ru.mcs.core.editor;

import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DefaultActionGroup;
import ru.mcs.spi.editor.FileEditor;

/** Встроенные действия меню File и контекстного меню вкладок. */
public final class EditorActions {

    private EditorActions() {
    }

    public static void register(ActionManager am, FileEditorManagerImpl em, JFrame frame) {
        Path[] lastDir = {Path.of(System.getProperty("user.home"))};

        am.registerAction("core.file.open", new AnAction("Open File\u2026") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                JFileChooser chooser = new JFileChooser(lastDir[0].toFile());
                if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                Path file = chooser.getSelectedFile().toPath();
                if (file.toAbsolutePath().getParent() != null) {
                    lastDir[0] = file.toAbsolutePath().getParent();
                }
                if (!em.openFile(file)) {
                    JOptionPane.showMessageDialog(frame, "Cannot open: " + file
                            + "\n(no suitable editor, file too large or not UTF-8)", "Open", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        am.registerAction("core.file.save", new AnAction("Save") {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(em.selectedEditor().map(FileEditor::isModified).orElse(false));
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                em.selectedEditor().ifPresent(ed -> save(frame, ed));
            }
        });

        am.registerAction("core.file.saveAll", new AnAction("Save All") {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(em.hasModified());
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                try {
                    em.saveAll();
                } catch (IOException ex) {
                    showError(frame, ex);
                }
            }
        });

        am.registerAction("core.tab.close", new AnAction("Close Tab") {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(em.getSelectedFile().isPresent());
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                em.getSelectedFile().ifPresent(em::closeFile);
            }
        });

        am.registerAction("core.tab.pin", new AnAction("Pin Tab") {
            @Override
            public void update(AnActionEvent e) {
                boolean selected = em.getSelectedFile().isPresent();
                boolean pinned = em.getSelectedFile().map(em::isPinned).orElse(false);
                e.getPresentation().setEnabled(selected);
                e.getPresentation().setText(pinned ? "Unpin Tab" : "Pin Tab");
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                em.getSelectedFile().ifPresent(f -> em.setPinned(f, !em.isPinned(f)));
            }
        });

        DefaultActionGroup file = (DefaultActionGroup) am.getAction(ActionIds.FILE_MENU);
        am.addToGroup(ActionIds.FILE_MENU, "core.file.open", Constraints.LAST);
        am.addToGroup(ActionIds.FILE_MENU, "core.file.save", Constraints.LAST);
        am.addToGroup(ActionIds.FILE_MENU, "core.file.saveAll", Constraints.LAST);
        file.addSeparator();
        am.addToGroup(ActionIds.FILE_MENU, "core.tab.close", Constraints.LAST);
        am.addToGroup(ActionIds.FILE_MENU, "core.tab.pin", Constraints.LAST);
        file.addSeparator();

        am.addToGroup(ActionIds.EDITOR_TAB_POPUP, "core.tab.close", Constraints.LAST);
        am.addToGroup(ActionIds.EDITOR_TAB_POPUP, "core.tab.pin", Constraints.LAST);
    }

    private static void save(JFrame frame, FileEditor editor) {
        try {
            editor.save();
        } catch (IOException ex) {
            showError(frame, ex);
        }
    }

    private static void showError(JFrame frame, IOException ex) {
        JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage(), "Save", JOptionPane.ERROR_MESSAGE);
    }
}
