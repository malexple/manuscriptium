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
    private final FileEditorManagerImpl editors;
    private final SessionStore sessionStore;
    private final StateManagerImpl state;
    private final VaultState vaultState;
    private final StatusBarModel statusBar;
    private final NotificationManagerImpl notifications;
    private final VaultTreePanel treePanel;

    private VaultService current;
    private boolean busy;

    public VaultController(JFrame frame, ActionManagerImpl actions, ToolWindowManagerImpl toolWindows,
                           FileEditorManagerImpl editors, SessionStore sessionStore, StateManagerImpl state,
                           VaultState vaultState, StatusBarModel statusBar, NotificationManagerImpl notifications) {
        this.frame = frame;
        this.editors = editors;
        this.sessionStore = sessionStore;
        this.state = state;
        this.vaultState = vaultState;
        this.statusBar = statusBar;
        this.notifications = notifications;
        this.treePanel = new VaultTreePanel(editors, this::moveOrClone);

        toolWindows.register("core", ToolWindowSpec.of("core.vault", Messages.get("vault.title"),
                        ToolWindowAnchor.LEFT, () -> treePanel)
                .withIcon(Icons.lucide("folder-open"))
                .withShowByDefault(true));
        registerOpenAction(actions);
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
        if (!editors.confirmAll()) {
            return;
        }
        busy = true;
        statusBar.setMessage(Messages.get("vault.indexing", normalized));
        new SwingWorker<VaultService, Void>() {
            @Override
            protected VaultService doInBackground() throws Exception {
                return VaultService.open(normalized, snapshot -> snapshotChanged(normalized, snapshot));
            }

            @Override
            protected void done() {
                VaultService opened = null;
                try {
                    opened = get();
                    if (!editors.confirmAll()) {
                        opened.close();
                        return;
                    }
                    switchVault(opened);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (opened != null) {
                        opened.close();
                    }
                    openFailed(normalized, e, interactive);
                } catch (ExecutionException | RuntimeException e) {
                    if (opened != null) {
                        opened.close();
                    }
                    openFailed(normalized, e, interactive);
                } finally {
                    busy = false;
                    if (current == null) {
                        restoreSession();
                    }
                }
            }
        }.execute();
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
        if (previous != null) {
            previous.close();
        }
        vaultState.setVault(opened.root());
        state.changeVaultDirectory(VaultPaths.stateDir(opened.root()));
        treePanel.setVault(opened.root(), opened.snapshot());
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
            } else if (!changed) {
                statusBar.setMessage(Messages.get("vault.drop.rejected"));
            } else {
                statusBar.setMessage(Messages.get(clone ? "vault.drop.cloned" : "vault.drop.moved"));
            }
        }));
        return result;
    }

    private void openFailed(Path root, Exception error, boolean interactive) {
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

    private static String displayName(Path root) {
        Path name = root.getFileName();
        return name != null ? name.toString() : root.toString();
    }

    @Override
    public void close() {
        if (current != null) {
            current.close();
            current = null;
        }
    }
}
