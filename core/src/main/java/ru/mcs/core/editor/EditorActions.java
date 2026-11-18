package ru.mcs.core.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.swing.action.DefaultActionGroup;
import ru.mcs.spi.swing.editor.FileEditor;

/** Встроенные действия меню File и контекстного меню вкладок. */
public final class EditorActions {

    private EditorActions() {
    }

    public static void register(ActionManager am, FileEditorManagerImpl em, JFrame frame) {
        Path[] lastDir = {Path.of(System.getProperty("user.home"))};

        am.registerAction("core.file.new", new AnAction(Messages.get("file.new"), null, Icons.lucide("scroll-text")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                JFileChooser chooser = new JFileChooser(lastDir[0].toFile());
                chooser.setDialogTitle(Messages.get("file.new.chooser"));
                if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                Path file = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
                if (file.getParent() != null) {
                    lastDir[0] = file.getParent();
                }
                new SwingWorker<Path, Void>() {
                    @Override
                    protected Path doInBackground() throws Exception {
                        return createNewFile(file);
                    }

                    @Override
                    protected void done() {
                        try {
                            Path created = get();
                            if (!em.openFile(created)) {
                                showNewFileError(frame, new IOException(
                                        Messages.get("file.open.error", created)));
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            showNewFileError(frame, ex);
                        } catch (ExecutionException | RuntimeException ex) {
                            showNewFileError(frame, ex);
                        }
                    }
                }.execute();
            }
        });

        am.registerAction("core.file.open", new AnAction(Messages.get("file.open"), null, Icons.lucide("folder-open")) {
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
                    JOptionPane.showMessageDialog(frame, Messages.get("file.open.error", file),
                            Messages.get("file.open.error.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        am.registerAction("core.file.save", new AnAction(Messages.get("file.save"), null, Icons.lucide("save")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(em.selectedEditor().map(FileEditor::isModified).orElse(false));
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                em.selectedEditor().ifPresent(ed -> save(frame, ed));
            }
        });

        am.registerAction("core.file.saveAll", new AnAction(Messages.get("file.saveAll"), null, Icons.lucide("save-all")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(em.hasModified());
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                em.saveAllAsync().whenComplete((ignored, error) -> {
                    if (error != null) {
                        showError(frame, saveError(error));
                    }
                });
            }
        });

        am.registerAction("core.tab.close", new AnAction(Messages.get("tab.close"), null, Icons.lucide("x")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(em.getSelectedFile().isPresent());
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                em.getSelectedFile().ifPresent(em::closeFileAsync);
            }
        });

        am.registerAction("core.tab.pin", new AnAction(Messages.get("tab.pin"), null, Icons.lucide("pin")) {
            @Override
            public void update(AnActionEvent e) {
                boolean selected = em.getSelectedFile().isPresent();
                boolean pinned = em.getSelectedFile().map(em::isPinned).orElse(false);
                e.getPresentation().setEnabled(selected);
                e.getPresentation().setText(Messages.get(pinned ? "tab.unpin" : "tab.pin"));
                e.getPresentation().setIcon(Icons.lucide(pinned ? "pin-off" : "pin"));
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                em.getSelectedFile().ifPresent(f -> em.setPinned(f, !em.isPinned(f)));
            }
        });

        // Меню, toolbar и popup связывает DesktopMetadata после регистрации реализаций.
    }

    /** Создаёт пустой файл атомарно относительно других создателей и никогда не перезаписывает существующий. */
    static Path createNewFile(Path file) throws IOException {
        return Files.createFile(file.toAbsolutePath().normalize());
    }

    private static void showNewFileError(JFrame frame, Throwable error) {
        IOException failure = saveError(error);
        JOptionPane.showMessageDialog(frame, Messages.get("file.new.error", failure.getMessage()),
                Messages.get("file.new.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    private static void save(JFrame frame, FileEditor editor) {
        editor.saveAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                showError(frame, saveError(error));
            }
        });
    }

    private static IOException saveError(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause instanceof IOException ? (IOException) cause : new IOException(cause);
    }

    private static void showError(JFrame frame, IOException ex) {
        JOptionPane.showMessageDialog(frame, Messages.get("save.error", ex.getMessage()),
                Messages.get("save.error.title"), JOptionPane.ERROR_MESSAGE);
    }
}
