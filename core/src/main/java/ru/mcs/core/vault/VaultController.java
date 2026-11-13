package ru.mcs.core.vault;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.editor.ObsidianLinkNavigator;
import ru.mcs.core.editor.SessionStore;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;
import ru.mcs.core.notification.NotificationManagerImpl;
import ru.mcs.core.state.StateManagerImpl;
import ru.mcs.core.statusbar.StatusBarModel;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.ui.NotificationType;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.ui.ToolWindowSpec;

/** Координирует UI, сессию и жизненный цикл Vault. Индексирование никогда не выполняется на EDT. */
public final class VaultController implements AutoCloseable {

    private final JFrame frame;
    private final ActionManagerImpl actions;
    private final FileEditorManagerImpl editors;
    private final SessionStore sessionStore;
    private final StateManagerImpl state;
    private final VaultState vaultState;
    private final StatusBarModel statusBar;
    private final NotificationManagerImpl notifications;
    private final ObsidianLinkNavigator obsidianLinks;
    private final VaultTreePanel treePanel;
    private final VaultSearchPanel searchPanel;

    private VaultService current;
    private boolean busy;
    private boolean creatingNote;

    public VaultController(JFrame frame, ActionManagerImpl actions, ToolWindowManagerImpl toolWindows,
                           FileEditorManagerImpl editors, SessionStore sessionStore, StateManagerImpl state,
                           VaultState vaultState, StatusBarModel statusBar, NotificationManagerImpl notifications,
                           ObsidianLinkNavigator obsidianLinks) {
        this.frame = frame;
        this.actions = actions;
        this.editors = editors;
        this.sessionStore = sessionStore;
        this.state = state;
        this.vaultState = vaultState;
        this.statusBar = statusBar;
        this.notifications = notifications;
        this.obsidianLinks = obsidianLinks;
        this.obsidianLinks.setStatusHandler(statusBar::setMessage);
        this.treePanel = new VaultTreePanel(editors, this::moveOrClone, this::newMarkdown,
                this::addLink, this::moveLink);
        this.searchPanel = new VaultSearchPanel(this::openSearchResult);

        toolWindows.register("core", ToolWindowSpec.of("core.vault", Messages.get("vault.title"),
                        ToolWindowAnchor.LEFT, () -> treePanel)
                .withIcon(Icons.lucide("folder-open"))
                .withShowByDefault(true));
        toolWindows.register("core", ToolWindowSpec.of("core.search", Messages.get("vault.search.title"),
                        ToolWindowAnchor.BOTTOM, () -> searchPanel)
                .withIcon(Icons.lucide("search")));
        registerOpenAction(actions);
        registerNewNoteAction(actions);
        registerSearchAction(actions, toolWindows);
    }

    /** Восстанавливает последний Vault после загрузки провайдеров редакторов всеми плагинами. */
    public void openInitial() {
        if (vaultState.vault().isPresent()) {
            open(vaultState.vault().orElseThrow(), false);
        } else {
            restoreSession();
        }
    }

    public void open(Path root) {
        open(root, true);
    }

    private void open(Path root, boolean interactive) {
        Path normalized = VaultPaths.normalize(root);
        if (busy || current != null && current.root().equals(normalized)) {
            return;
        }
        busy = true;
        editors.confirmAllAsync().whenComplete((confirmed, error) -> {
            if (error != null || !confirmed) {
                if (error != null) {
                    openFailed(normalized, error, interactive);
                }
                finishOpen();
                return;
            }
            startOpen(normalized, interactive);
        });
    }

    private void startOpen(Path normalized, boolean interactive) {
        statusBar.setMessage(Messages.get("vault.indexing", normalized));
        new SwingWorker<VaultService, Void>() {
            @Override
            protected VaultService doInBackground() throws Exception {
                return VaultService.open(normalized, snapshot -> snapshotChanged(normalized, snapshot));
            }

            @Override
            protected void done() {
                VaultService opened;
                try {
                    opened = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    openFailed(normalized, e, interactive);
                    finishOpen();
                    return;
                } catch (ExecutionException | RuntimeException e) {
                    openFailed(normalized, e, interactive);
                    finishOpen();
                    return;
                }
                editors.confirmAllAsync().whenComplete((confirmed, error) -> {
                    if (error != null || !confirmed) {
                        opened.close();
                        if (error != null) {
                            openFailed(normalized, error, interactive);
                        }
                        finishOpen();
                        return;
                    }
                    try {
                        switchVault(opened);
                    } catch (RuntimeException e) {
                        opened.close();
                        openFailed(normalized, e, interactive);
                    } finally {
                        finishOpen();
                    }
                });
            }
        }.execute();
    }

    private void finishOpen() {
        busy = false;
        if (current == null) {
            restoreSession();
        }
    }

    private void switchVault(VaultService opened) {
        // Старая live-сессия сохраняется до закрытия вкладок; затем StateManager загружает новый Vault.
        state.saveChanged();
        sessionStore.deactivate();
        for (Path file : editors.getOpenFiles()) {
            editors.closeFile(file);
        }
        VaultService previous = current;
        current = opened;
        actions.updateActions();
        if (previous != null) {
            previous.close();
        }
        vaultState.setVault(opened.root());
        state.changeVaultDirectory(VaultPaths.stateDir(opened.root()));
        obsidianLinks.setVault(opened.root(), opened.snapshot().notes());
        treePanel.setVault(opened.root(), opened.snapshot());
        searchPanel.setVault(opened);
        frame.setTitle(Messages.get("vault.window.title", displayName(opened.root())));
        restoreSession();
        state.saveChanged();
        updateStatus(opened.snapshot());
    }

    private void restoreSession() {
        editors.restoreSession(sessionStore.loaded());
        sessionStore.activate(editors::currentSession);
    }

    private void snapshotChanged(Path root, VaultSnapshot snapshot) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (current == null || !current.root().equals(VaultPaths.normalize(root))) {
                return;
            }
            obsidianLinks.updateNotes(snapshot.notes());
            treePanel.setVault(current.root(), snapshot);
            updateStatus(snapshot);
        });
    }

    private void updateStatus(VaultSnapshot snapshot) {
        if (snapshot.viewErrors().isEmpty()) {
            statusBar.setMessage(Messages.get("vault.status.views", snapshot.notes().size(), snapshot.views().size()));
        } else {
            statusBar.setMessage(Messages.get("vault.status.viewErrors", snapshot.notes().size(),
                    snapshot.views().size(), snapshot.viewErrors().size()));
        }
    }

    private CompletableFuture<Boolean> moveOrClone(String viewId, List<Integer> sourcePath, String sourceFile,
                                                    List<Integer> targetPath, boolean clone) {
        VaultService service = current;
        if (service == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = service.moveOrClone(viewId, sourcePath, sourceFile, targetPath, clone);
        linkResult(service, result, clone);
        return result;
    }

    private void linkResult(VaultService service, CompletableFuture<?> result, boolean clone) {
        result.whenComplete((changed, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (current != service) {
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException
                        && error.getCause() != null ? error.getCause() : error;
                String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                notifications.notify("core", Messages.get("vault.drop.error.title"),
                        Messages.get("vault.drop.error", detail), NotificationType.ERROR);
                statusBar.setMessage(Messages.get("vault.drop.error", detail));
            } else if (changed == null || changed instanceof Boolean value && !value) {
                statusBar.setMessage(Messages.get("vault.drop.rejected"));
            } else {
                if (changed instanceof String viewId) {
                    treePanel.selectView(viewId);
                }
                statusBar.setMessage(Messages.get(clone ? "vault.drop.cloned" : "vault.drop.moved"));
            }
        }));
    }

    private void moveLink(String file, String sourceViewId, List<Integer> sourcePath,
                          String targetViewId, String newViewName, List<Integer> targetPath) {
        VaultService service = current;
        if (service == null) {
            return;
        }
        linkResult(service, service.moveLink(file, sourceViewId, sourcePath,
                targetViewId, newViewName, targetPath), false);
    }

    private void addLink(String file, String viewId, String newViewName, List<Integer> targetPath) {
        VaultService service = current;
        if (service == null) {
            return;
        }
        linkResult(service, service.addLink(file, viewId, newViewName, targetPath), true);
    }

    private void newMarkdown(Path directory) {
        VaultService service = current;
        if (service == null || creatingNote) {
            return;
        }
        Path root = service.root();
        Path parent = directory != null ? directory : treePanel.newNoteDirectory();
        String defaultPath = defaultNotePath(root, parent);
        Object answer = JOptionPane.showInputDialog(frame, Messages.get("vault.newNote.message"),
                Messages.get("vault.newNote.title"), JOptionPane.QUESTION_MESSAGE,
                null, null, defaultPath);
        if (answer == null) {
            return;
        }
        creatingNote = true;
        actions.updateActions();
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return MarkdownNoteCreator.create(root, answer.toString());
            }

            @Override
            protected void done() {
                try {
                    Path created = get();
                    if (current == service) {
                        editors.openFile(created);
                        statusBar.setMessage(Messages.get("vault.newNote.created",
                                VaultPaths.relativePath(root, created)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    noteFailed(e);
                } catch (ExecutionException | RuntimeException e) {
                    noteFailed(e);
                } finally {
                    creatingNote = false;
                    actions.updateActions();
                }
            }
        }.execute();
    }

    private static String defaultNotePath(Path root, Path parent) {
        Path directory = parent == null ? root : parent;
        String prefix = directory.equals(root) ? "" : VaultPaths.relativePath(root, directory) + "/";
        for (int i = 1; i < 1000; i++) {
            String name = i == 1 ? "untitled.md" : "untitled-" + i + ".md";
            if (!java.nio.file.Files.exists(directory.resolve(name), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return prefix + name;
            }
        }
        return prefix + "untitled.md";
    }

    private void noteFailed(Exception error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        notifications.notify("core", Messages.get("vault.newNote.error.title"),
                Messages.get("vault.newNote.error", detail), NotificationType.ERROR);
        JOptionPane.showMessageDialog(frame, Messages.get("vault.newNote.error", detail),
                Messages.get("vault.newNote.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    private void openFailed(Path root, Throwable error, boolean interactive) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        String detail = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        notifications.notify("core", Messages.get("vault.open.error.title"),
                Messages.get("vault.open.error", root, detail), NotificationType.ERROR);
        if (interactive) {
            JOptionPane.showMessageDialog(frame, Messages.get("vault.open.error", root, detail),
                    Messages.get("vault.open.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void registerOpenAction(ActionManagerImpl actions) {
        actions.registerAction("core.vault.open", new AnAction(Messages.get("vault.open"), null,
                Icons.lucide("folder-open")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(!busy);
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                Path start = current != null ? current.root() : Path.of(System.getProperty("user.home"));
                JFileChooser chooser = new JFileChooser(start.toFile());
                chooser.setDialogTitle(Messages.get("vault.open.chooser"));
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    open(chooser.getSelectedFile().toPath());
                }
            }
        });
        actions.addToGroup(ActionIds.FILE_MENU, "core.vault.open", Constraints.FIRST);
        actions.addToGroup(ActionIds.MAIN_TOOLBAR, "core.vault.open", Constraints.FIRST);
    }

    private void registerNewNoteAction(ActionManagerImpl actions) {
        actions.registerAction("core.vault.newNote", new AnAction(Messages.get("vault.newNote"), null,
                Icons.lucide("scroll-text")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(current != null && !creatingNote);
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                newMarkdown(null);
            }
        });
        actions.addToGroup(ActionIds.FILE_MENU, "core.vault.newNote", Constraints.after("core.vault.open"));
        actions.addToGroup(ActionIds.MAIN_TOOLBAR, "core.vault.newNote", Constraints.after("core.vault.open"));
    }

    private void registerSearchAction(ActionManagerImpl actions, ToolWindowManagerImpl toolWindows) {
        actions.registerAction("core.search.open", new AnAction(Messages.get("vault.search.action"), null,
                Icons.lucide("search")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                toolWindows.get("core.search").show();
                javax.swing.SwingUtilities.invokeLater(searchPanel::focusQuery);
            }
        });
        actions.addToGroup(ActionIds.TOOLS_MENU, "core.search.open", Constraints.FIRST);
        actions.addToGroup(ActionIds.MAIN_TOOLBAR, "core.search.open", Constraints.after("core.vault.newNote"));
    }

    private void openSearchResult(VaultSearchResult result) {
        VaultService service = current;
        if (service == null) {
            return;
        }
        Path file = result.file().toAbsolutePath().normalize();
        if (!file.startsWith(service.root())) {
            return;
        }
        editors.openFile(file);
        statusBar.setMessage(Messages.get("vault.search.opened", result.path()));
    }

    private static String displayName(Path root) {
        Path name = root.getFileName();
        return name != null ? name.toString() : root.toString();
    }

    @Override
    public void close() {
        if (current != null) {
            current.close();
            current = null;
            searchPanel.setVault(null);
            obsidianLinks.clearVault();
            actions.updateActions();
        }
    }
}
