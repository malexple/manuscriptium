package ru.mcs.core.vault;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;

/** Панель Vault: ленивое физическое дерево, вкладки виртуальных views и неблокирующие ошибки YAML. */
final class VaultTreePanel extends JPanel {

    interface NewNoteHandler {
        void newNote(Path directory);
    }

    interface CloneLinkHandler {
        void cloneLink(String file, String viewId, String newViewName, List<Integer> targetPath);
    }

    interface MoveLinkHandler {
        void moveLink(String file, String sourceViewId, List<Integer> sourcePath,
                      String targetViewId, String newViewName, List<Integer> targetPath);
    }

    private static final String EMPTY = "empty";
    private static final String TABS = "tabs";
    private static final System.Logger LOG = System.getLogger(VaultTreePanel.class.getName());

    private final CardLayout cards = new CardLayout();
    private final VaultTreeModel model = new VaultTreeModel();
    private final JTree tree = new JTree(model);
    private final JScrollPane physicalScroll = new JScrollPane(tree);
    private final JTabbedPane tabs = new JTabbedPane();
    private final FileEditorManagerImpl editors;
    private final ViewTreePanel.DropHandler dropHandler;
    private final NewNoteHandler newNoteHandler;
    private final CloneLinkHandler cloneLinkHandler;
    private final MoveLinkHandler moveLinkHandler;
    private final Map<String, ViewTreePanel> viewPanels = new HashMap<>();
    private List<ViewDefinition> views = List.of();
    private final Set<Path> pending = new HashSet<>();
    private Set<Path> desiredExpanded = new HashSet<>();
    private Path desiredSelection;

    VaultTreePanel(FileEditorManagerImpl editors, ViewTreePanel.DropHandler dropHandler,
                   NewNoteHandler newNoteHandler, CloneLinkHandler cloneLinkHandler,
                   MoveLinkHandler moveLinkHandler) {
        super(new BorderLayout());
        this.editors = editors;
        this.dropHandler = dropHandler;
        this.newNoteHandler = newNoteHandler;
        this.cloneLinkHandler = cloneLinkHandler;
        this.moveLinkHandler = moveLinkHandler;
        setLayout(cards);
        JLabel empty = new JLabel(Messages.get("vault.empty"), SwingConstants.CENTER);
        empty.setBorder(new EmptyBorder(16, 16, 16, 16));
        add(empty, EMPTY);
        tabs.addTab(Messages.get("vault.files"), physicalScroll);
        add(tabs, TABS);
        cards.show(this, EMPTY);

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new PhysicalRenderer());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) {
                if (event.getPath().getLastPathComponent() instanceof DefaultMutableTreeNode node) {
                    loadChildren(node);
                }
            }

            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) {
                // Свёрнутые дети остаются материализованными до следующего снимка.
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelected();
                }
            }
        });
        TreePopupSupport.install(tree, this::physicalPopup);
        tree.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "mcs.openVaultNote");
        tree.getActionMap().put("mcs.openVaultNote", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelected();
            }
        });
    }

    void setVault(Path root, VaultSnapshot snapshot) {
        Path normalized = root == null ? null : VaultPaths.normalize(root);
        if (normalized == null || !normalized.equals(model.vault())) {
            desiredExpanded = new HashSet<>();
            desiredSelection = null;
        } else {
            desiredExpanded = expandedFiles();
            Path selected = selectedFile();
            desiredSelection = selected;
        }
        model.setVault(normalized, snapshot.notes());
        pending.clear();
        if (normalized == null) {
            desiredExpanded = Set.of();
            desiredSelection = null;
            views = List.of();
            cards.show(this, EMPTY);
            return;
        }
        views = snapshot.views();
        updateViews(normalized, snapshot);
        desiredExpanded.add(normalized);
        expand(normalized);
        cards.show(this, TABS);
    }

    private void updateViews(Path root, VaultSnapshot snapshot) {
        String selectedId = selectedViewId();
        for (int i = tabs.getTabCount() - 1; i >= 1; i--) {
            tabs.removeTabAt(i);
        }
        Set<String> notePaths = new HashSet<>();
        for (VaultNote note : snapshot.notes()) {
            notePaths.add(note.path());
        }
        Set<String> clonedFiles = ViewOperations.clonedFiles(snapshot.views());
        Set<String> present = new HashSet<>();
        for (ViewDefinition view : snapshot.views()) {
            present.add(view.id());
            ViewTreePanel panel = viewPanels.computeIfAbsent(view.id(), ignored -> new ViewTreePanel(editors, dropHandler));
            panel.setView(root, view, notePaths, clonedFiles);
            tabs.addTab(view.name(), panel);
        }
        viewPanels.keySet().retainAll(present);
        if (!snapshot.viewErrors().isEmpty()) {
            JTextArea errors = new JTextArea(String.join("\n\n", snapshot.viewErrors()));
            errors.setEditable(false);
            errors.setLineWrap(true);
            errors.setWrapStyleWord(true);
            errors.setBorder(new EmptyBorder(8, 8, 8, 8));
            JScrollPane scroll = new JScrollPane(errors);
            scroll.setName("view-errors");
            tabs.addTab(Messages.get("vault.view.errors", snapshot.viewErrors().size()), scroll);
        }
        restoreSelectedTab(selectedId);
    }

    void selectView(String id) {
        for (int i = 1; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof ViewTreePanel panel
                    && panel.tree().getModel() instanceof ViewTreeModel viewModel
                    && viewModel.view() != null && viewModel.view().id().equals(id)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private String selectedViewId() {
        java.awt.Component selected = tabs.getSelectedComponent();
        if (selected instanceof ViewTreePanel panel && panel.tree().getModel() instanceof ViewTreeModel viewModel
                && viewModel.view() != null) {
            return viewModel.view().id();
        }
        if (selected instanceof JScrollPane scroll && "view-errors".equals(scroll.getName())) {
            return "view-errors";
        }
        return null;
    }

    private void restoreSelectedTab(String selectedId) {
        if (selectedId == null) {
            tabs.setSelectedIndex(0);
            return;
        }
        for (int i = 1; i < tabs.getTabCount(); i++) {
            java.awt.Component component = tabs.getComponentAt(i);
            if ("view-errors".equals(selectedId) && component instanceof JScrollPane scroll
                    && "view-errors".equals(scroll.getName())) {
                tabs.setSelectedIndex(i);
                return;
            }
            if (component instanceof ViewTreePanel panel
                    && panel.tree().getModel() instanceof ViewTreeModel viewModel
                    && viewModel.view() != null && viewModel.view().id().equals(selectedId)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
        tabs.setSelectedIndex(0);
    }

    private void loadChildren(DefaultMutableTreeNode node) {
        if (!(node.getUserObject() instanceof VaultTreeModel.Node value) || !value.directory()
                || model.isLoaded(value.file()) || !pending.add(value.file())) {
            return;
        }
        long generation = model.generation();
        new SwingWorker<List<VaultTreeModel.Node>, Void>() {
            @Override
            protected List<VaultTreeModel.Node> doInBackground() {
                return model.children(value.file(), generation);
            }

            @Override
            protected void done() {
                pending.remove(value.file());
                try {
                    if (!model.installChildren(value.file(), generation, get())) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    LOG.log(System.Logger.Level.WARNING, "Cannot materialize vault tree: " + value.file(), e);
                    return;
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                    if (child.getUserObject() instanceof VaultTreeModel.Node childValue
                            && childValue.directory() && desiredExpanded.contains(childValue.file())) {
                        expand(childValue.file());
                    }
                }
                if (desiredSelection != null) {
                    DefaultMutableTreeNode selected = model.findNode(desiredSelection);
                    if (selected != null) {
                        TreePath path = VaultTreeModel.path(selected);
                        tree.setSelectionPath(path);
                        tree.scrollPathToVisible(path);
                        desiredSelection = null;
                    }
                }
            }
        }.execute();
    }

    private Set<Path> expandedFiles() {
        Set<Path> result = new HashSet<>();
        Object root = model.getRoot();
        if (!(root instanceof DefaultMutableTreeNode node)) {
            return result;
        }
        Enumeration<TreePath> paths = tree.getExpandedDescendants(VaultTreeModel.path(node));
        if (paths != null) {
            while (paths.hasMoreElements()) {
                Path file = file(paths.nextElement());
                if (file != null) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    private void expand(Path file) {
        DefaultMutableTreeNode node = model.findNode(file);
        if (node != null) {
            tree.expandPath(VaultTreeModel.path(node));
            // JTree может автоматически раскрыть root при setRoot без TreeWillExpandEvent.
            loadChildren(node);
        }
    }

    private Path selectedFile() {
        return file(tree.getSelectionPath());
    }

    private static Path file(TreePath path) {
        VaultTreeModel.Node value = physicalValue(path);
        return value == null ? null : value.file();
    }

    private static VaultTreeModel.Node physicalValue(TreePath path) {
        if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof VaultTreeModel.Node value) {
            return value;
        }
        return null;
    }

    JPopupMenu physicalPopup(TreePath path) {
        VaultTreeModel.Node value = physicalValue(path);
        if (value == null) {
            return null;
        }
        JPopupMenu popup = new JPopupMenu();
        if (!value.directory()) {
            JMenuItem open = new JMenuItem(Messages.get("vault.note.open"));
            open.addActionListener(e -> editors.openFile(value.file()));
            popup.add(open);
            popup.addSeparator();
            String file = VaultPaths.relativePath(model.vault(), value.file());
            popup.add(clonePhysicalAction(file));
            popup.add(movePhysicalAction(file));
            popup.addSeparator();
        }
        JMenuItem create = new JMenuItem(Messages.get("vault.newNote"));
        Path directory = value.directory() ? value.file() : value.file().getParent();
        create.addActionListener(e -> newNoteHandler.newNote(directory));
        popup.add(create);
        return popup;
    }

    private JMenuItem clonePhysicalAction(String file) {
        JMenuItem action = new JMenuItem(Messages.get("vault.link.clone"));
        action.addActionListener(e -> PhysicalLinkTargetChooser.choose(SwingUtilities.getWindowAncestor(this), views, file)
                .ifPresent(target -> cloneLinkHandler.cloneLink(file, target.viewId(),
                        target.newViewName(), target.targetPath())));
        return action;
    }

    private JMenuItem movePhysicalAction(String file) {
        JMenuItem action = new JMenuItem(Messages.get("vault.link.move"));
        List<PhysicalLinkSources.Source> sources = PhysicalLinkSources.find(views, file);
        action.setEnabled(!sources.isEmpty());
        action.addActionListener(e -> {
            Optional<PhysicalLinkSources.Source> selected = PhysicalLinkSources.choose(
                    SwingUtilities.getWindowAncestor(this), sources);
            selected.ifPresent(source -> PhysicalLinkTargetChooser.chooseMove(SwingUtilities.getWindowAncestor(this),
                            views, source)
                    .ifPresent(target -> moveLinkHandler.moveLink(file, source.view().id(), source.path(),
                            target.viewId(), target.newViewName(), target.targetPath())));
        });
        return action;
    }

    Path newNoteDirectory() {
        VaultTreeModel.Node value = physicalValue(tree.getSelectionPath());
        if (value == null) {
            return model.vault();
        }
        return value.directory() ? value.file() : value.file().getParent();
    }

    private void openSelected() {
        VaultTreeModel.Node value = physicalValue(tree.getSelectionPath());
        if (value != null && !value.directory()) {
            editors.openFile(value.file());
        }
    }

    JTree physicalTree() {
        return tree;
    }

    JTabbedPane tabs() {
        return tabs;
    }

    private static final class PhysicalRenderer extends DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                              boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
            if (value instanceof DefaultMutableTreeNode node) {
                if (node.getUserObject() instanceof VaultTreeModel.Node n && !n.directory()) {
                    setIcon(Icons.lucide("scroll-text"));
                } else if (node.getUserObject() == null) {
                    setText(Messages.get("vault.tree.loading"));
                    setIcon(null);
                }
            }
            return this;
        }
    }
}
