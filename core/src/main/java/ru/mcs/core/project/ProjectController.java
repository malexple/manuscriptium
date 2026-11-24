package ru.mcs.core.project;

import ru.mcs.spi.project.FullTextIndexFactory;
import ru.mcs.spi.project.ProjectSearch;

import ru.mcs.spi.project.ViewEntry;
import ru.mcs.spi.project.ViewDefinition;
import java.nio.file.Files;
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
import ru.mcs.spi.project.BranchStatisticsService;
import ru.mcs.spi.project.ProjectFileFactory;
import ru.mcs.spi.service.ServiceRegistry;
import ru.mcs.spi.swing.project.BookService;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.ui.NotificationType;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.swing.ui.ToolWindowSpec;

/** Координирует UI, сессию и жизненный цикл Project. Индексирование никогда не выполняется на EDT. */
public final class ProjectController implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ProjectController.class.getName());

    private final JFrame frame;
    private final ActionManagerImpl actions;
    private final FileEditorManagerImpl editors;
    private final SessionStore sessionStore;
    private final StateManagerImpl state;
    private final ProjectState projectState;
    private final StatusBarModel statusBar;
    private final NotificationManagerImpl notifications;
    private final CoreProjectAccess projectAccess;
    private final ProjectIndexContributors indexContributors;
    private final ServiceRegistry services;
    private final ProjectTreePanel treePanel;

    private ProjectService current;
    private boolean busy;
    private boolean creatingProject;
    private boolean creatingNote;

    public ProjectController(JFrame frame, ActionManagerImpl actions, ToolWindowManagerImpl toolWindows,
                           FileEditorManagerImpl editors, SessionStore sessionStore, StateManagerImpl state,
                           ProjectState projectState, StatusBarModel statusBar, NotificationManagerImpl notifications,
                           CoreProjectAccess projectAccess,
                           ProjectIndexContributors indexContributors, ServiceRegistry services) {
        this.frame = frame;
        this.actions = actions;
        this.editors = editors;
        this.sessionStore = sessionStore;
        this.state = state;
        this.projectState = projectState;
        this.statusBar = statusBar;
        this.notifications = notifications;
        this.projectAccess = projectAccess;
        this.indexContributors = indexContributors;
        this.services = services;
        this.treePanel = new ProjectTreePanel(editors, this::moveOrClone, this::newDocument,
                this::addLink, this::moveLink, this::openBook, new ViewTreePanel.StatisticsHandler() {
            @Override
            public void physical(Path file) {
                showPhysicalStatistics(file);
            }

            @Override
            public void view(String viewId, List<Integer> nodePath) {
                showViewStatistics(viewId, nodePath);
            }
        });

        toolWindows.register("core", ToolWindowSpec.of("core.project", Messages.get("project.title"),
                        ToolWindowAnchor.LEFT, () -> treePanel)
                .withIcon(Icons.lucide("folder-open"))
                .withShowByDefault(true));
        registerOpenAction(actions);
        registerNewProjectAction(actions);
        registerNewNoteAction(actions);
    }

    /** Восстанавливает последний Project после загрузки провайдеров редакторов всеми плагинами. */
    public void openInitial() {
        if (projectState.project().isPresent()) {
            open(projectState.project().orElseThrow(), false);
        } else {
            restoreSession();
        }
    }

    public void open(Path root) {
        open(root, true);
    }

    private void open(Path root, boolean interactive) {
        Path normalized = ProjectPaths.normalize(root);
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
        statusBar.setMessage(Messages.get("project.indexing", normalized));
        new SwingWorker<ProjectService, Void>() {
            @Override
            protected ProjectService doInBackground() throws Exception {
                return ProjectService.open(normalized, snapshot -> snapshotChanged(normalized, snapshot), indexContributors,
                        services.find(FullTextIndexFactory.class).orElse(null));
            }

            @Override
            protected void done() {
                ProjectService opened;
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
                        switchProject(opened);
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

    private void switchProject(ProjectService opened) {
        // Старая live-сессия сохраняется до закрытия вкладок; затем StateManager загружает новый Project.
        state.saveChanged();
        sessionStore.deactivate();
        for (Path file : editors.getOpenFiles()) {
            editors.closeFile(file);
        }
        ProjectService previous = current;
        current = opened;
        actions.updateActions();
        if (previous != null) {
            previous.close();
        }
        projectState.setProject(opened.root());
        state.changeProjectDirectory(ProjectPaths.stateDir(opened.root()));
        projectAccess.setRoot(opened.root());
        projectAccess.update(opened.snapshot());
        treePanel.setProject(opened.root(), opened.snapshot());
        projectSearch().setCurrent(opened);
        frame.setTitle(Messages.get("project.window.title", displayName(opened.root())));
        restoreSession();
        state.saveChanged();
        updateStatus(opened.snapshot());
    }

    private void restoreSession() {
        editors.restoreSession(sessionStore.loaded());
        sessionStore.activate(editors::currentSession);
    }

    private void snapshotChanged(Path root, ProjectSnapshot snapshot) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (current == null || !current.root().equals(ProjectPaths.normalize(root))) {
                return;
            }
            projectAccess.update(snapshot);
            treePanel.setProject(current.root(), snapshot);
            updateStatus(snapshot);
        });
    }

    private static List<Path> notePaths(ProjectSnapshot snapshot) {
        return snapshot.notes().stream().map(ProjectNote::file).toList();
    }

    private void updateStatus(ProjectSnapshot snapshot) {
        if (snapshot.viewErrors().isEmpty()) {
            statusBar.setMessage(Messages.get("project.status.views", snapshot.notes().size(), snapshot.views().size()));
        } else {
            statusBar.setMessage(Messages.get("project.status.viewErrors", snapshot.notes().size(),
                    snapshot.views().size(), snapshot.viewErrors().size()));
        }
    }

    private void openBook(String viewId, List<Integer> nodePath) {
        ProjectService service = current;
        if (service == null) {
            return;
        }
        ViewDefinition view = service.snapshot().views().stream()
                .filter(candidate -> candidate.id().equals(viewId)).findFirst().orElse(null);
        if (view == null) {
            return;
        }
        String suffix = nodePath.isEmpty() ? "" : "-" + nodePath.stream().map(String::valueOf)
                .reduce((left, right) -> left + "-" + right).orElse("");
        Path key = service.root().resolve(".manuscriptium/books/" + viewId + suffix + ".book");
        ViewEntry selected = nodePath.isEmpty() ? null : ViewOperations.entryAt(view.entries(), nodePath);
        String name = selected == null ? view.name() : selected.title();
        services.find(BookService.class).ifPresent(books -> editors.openEditor(key,
                books.createEditor(key, name, service.root(), view, nodePath)));
    }

    private void showPhysicalStatistics(Path selected) {
        ProjectService service = current;
        if (service != null) {
            services.find(BranchStatisticsService.class).ifPresent(statistics -> showStatistics(service,
                    statistics.physical(selected, notePaths(service.snapshot()))));
        }
    }

    private void showViewStatistics(String viewId, List<Integer> nodePath) {
        ProjectService service = current;
        if (service == null) {
            return;
        }
        ViewDefinition view = service.snapshot().views().stream()
                .filter(candidate -> candidate.id().equals(viewId)).findFirst().orElse(null);
        if (view != null) {
            services.find(BranchStatisticsService.class).ifPresent(statistics -> showStatistics(service,
                    statistics.view(service.root(), view, nodePath)));
        }
    }

    private void showStatistics(ProjectService service,
                                CompletableFuture<BranchStatisticsService.Statistics> calculation) {
        statusBar.setMessage(Messages.get("branch.statistics.loading"));
        calculation.whenComplete((statistics, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (current != service) {
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException
                        && error.getCause() != null ? error.getCause() : error;
                String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                notifications.notify("core", Messages.get("branch.statistics.title"),
                        Messages.get("branch.statistics.error", detail), NotificationType.ERROR);
                statusBar.setMessage(Messages.get("branch.statistics.error", detail));
                return;
            }
            statusBar.setMessage(Messages.get("branch.statistics.ready"));
            JOptionPane.showMessageDialog(frame, Messages.get("branch.statistics.message",
                            statistics.files(), statistics.words(), statistics.characters(), statistics.readingMinutes()),
                    Messages.get("branch.statistics.title"), JOptionPane.INFORMATION_MESSAGE);
        }));
    }

    private CompletableFuture<Boolean> moveOrClone(String viewId, List<Integer> sourcePath, String sourceFile,
                                                    List<Integer> targetPath, boolean clone) {
        ProjectService service = current;
        if (service == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = service.moveOrClone(viewId, sourcePath, sourceFile, targetPath, clone);
        linkResult(service, result, clone);
        return result;
    }

    private void linkResult(ProjectService service, CompletableFuture<?> result, boolean clone) {
        result.whenComplete((changed, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (current != service) {
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException
                        && error.getCause() != null ? error.getCause() : error;
                String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                notifications.notify("core", Messages.get("project.drop.error.title"),
                        Messages.get("project.drop.error", detail), NotificationType.ERROR);
                statusBar.setMessage(Messages.get("project.drop.error", detail));
            } else if (changed == null || changed instanceof Boolean value && !value) {
                statusBar.setMessage(Messages.get("project.drop.rejected"));
            } else {
                if (changed instanceof String viewId) {
                    treePanel.selectView(viewId);
                }
                statusBar.setMessage(Messages.get(clone ? "project.drop.cloned" : "project.drop.moved"));
            }
        }));
    }

    private void moveLink(String file, String sourceViewId, List<Integer> sourcePath,
                          String targetViewId, String newViewName, List<Integer> targetPath) {
        ProjectService service = current;
        if (service == null) {
            return;
        }
        linkResult(service, service.moveLink(file, sourceViewId, sourcePath,
                targetViewId, newViewName, targetPath), false);
    }

    private void addLink(String file, String viewId, String newViewName, List<Integer> targetPath) {
        ProjectService service = current;
        if (service == null) {
            return;
        }
        linkResult(service, service.addLink(file, viewId, newViewName, targetPath), true);
    }

    private void newDocument(Path directory) {
        ProjectService service = current;
        ProjectFileFactory fileFactory = services.find(ProjectFileFactory.class).orElse(null);
        if (service == null || fileFactory == null || creatingNote) {
            return;
        }
        Path root = service.root();
        Path parent = directory != null ? directory : treePanel.newNoteDirectory();
        String defaultPath = fileFactory.defaultPath(root, parent);
        Object answer = JOptionPane.showInputDialog(frame, Messages.get("project.newNote.message"),
                Messages.get("project.newNote.title"), JOptionPane.QUESTION_MESSAGE,
                null, null, defaultPath);
        if (answer == null) {
            return;
        }
        creatingNote = true;
        actions.updateActions();
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return fileFactory.create(root, answer.toString());
            }

            @Override
            protected void done() {
                try {
                    Path created = get();
                    if (current == service) {
                        editors.openFile(created);
                        statusBar.setMessage(Messages.get("project.newNote.created",
                                ProjectPaths.relativePath(root, created)));
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

    private void noteFailed(Exception error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        notifications.notify("core", Messages.get("project.newNote.error.title"),
                Messages.get("project.newNote.error", detail), NotificationType.ERROR);
        JOptionPane.showMessageDialog(frame, Messages.get("project.newNote.error", detail),
                Messages.get("project.newNote.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    private void openFailed(Path root, Throwable error, boolean interactive) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        LOG.log(System.Logger.Level.WARNING, "Cannot open project: " + root, cause);
        String detail = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        notifications.notify("core", Messages.get("project.open.error.title"),
                Messages.get("project.open.error", root, detail), NotificationType.ERROR);
        if (interactive) {
            JOptionPane.showMessageDialog(frame, Messages.get("project.open.error", root, detail),
                    Messages.get("project.open.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void registerOpenAction(ActionManagerImpl actions) {
        actions.registerAction("core.project.open", new AnAction(Messages.get("project.open"), null,
                Icons.lucide("folder-open")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(!busy && !creatingProject);
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                Path start = current != null ? current.root() : Path.of(System.getProperty("user.home"));
                JFileChooser chooser = new JFileChooser(start.toFile());
                chooser.setDialogTitle(Messages.get("project.open.chooser"));
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    open(chooser.getSelectedFile().toPath());
                }
            }
        });
    }

    private void registerNewProjectAction(ActionManagerImpl actions) {
        actions.registerAction("core.project.new", new AnAction(Messages.get("project.new"), null,
                Icons.lucide("folder-open")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(!busy && !creatingProject);
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                Path defaultPath = Path.of(System.getProperty("user.home"), "Manuscriptium");
                Object answer = JOptionPane.showInputDialog(frame, Messages.get("project.new.message"),
                        Messages.get("project.new.title"), JOptionPane.QUESTION_MESSAGE,
                        null, null, defaultPath.toString());
                if (answer == null || answer.toString().isBlank()) {
                    return;
                }
                Path root;
                try {
                    root = Path.of(answer.toString()).toAbsolutePath().normalize();
                } catch (RuntimeException ex) {
                    openFailed(defaultPath, ex, true);
                    return;
                }
                creatingProject = true;
                actions.updateActions();
                new SwingWorker<Path, Void>() {
                    @Override
                    protected Path doInBackground() throws Exception {
                        return createProjectDirectory(root);
                    }

                    @Override
                    protected void done() {
                        creatingProject = false;
                        actions.updateActions();
                        try {
                            open(get());
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            openFailed(root, ex, true);
                        } catch (ExecutionException | RuntimeException ex) {
                            openFailed(root, ex, true);
                        }
                    }
                }.execute();
            }
        });
    }

    static Path createProjectDirectory(Path root) throws java.io.IOException {
        return Files.createDirectories(root.toAbsolutePath().normalize());
    }

    private void registerNewNoteAction(ActionManagerImpl actions) {
        actions.registerAction("core.project.newNote", new AnAction(Messages.get("project.newNote"), null,
                Icons.lucide("scroll-text")) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(current != null && !creatingNote
                        && services.find(ProjectFileFactory.class).isPresent());
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                newDocument(null);
            }
        });
    }

    private CoreProjectSearch projectSearch() {
        return (CoreProjectSearch) services.get(ProjectSearch.class);
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
            projectSearch().setCurrent(null);
            projectAccess.clear();
            actions.updateActions();
        }
    }
}
