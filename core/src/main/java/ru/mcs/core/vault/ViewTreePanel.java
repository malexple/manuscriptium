package ru.mcs.core.vault;

import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;

/** Одно виртуальное дерево с открытием файлов и внутренним DnD ссылок. */
final class ViewTreePanel extends JPanel {

    interface DropHandler {
        void drop(String viewId, List<Integer> sourcePath, String sourceFile,
                  List<Integer> targetPath, boolean clone);
    }

    private record ViewTransfer(String viewId, List<Integer> sourcePath, String sourceFile) {
    }

    private static final DataFlavor VIEW_FLAVOR = new DataFlavor(ViewTransfer.class, "Manuscriptium view link");

    private final ViewTreeModel model = new ViewTreeModel();
    private final JTree tree = new JTree(model);
    private final FileEditorManagerImpl editors;
    private final DropHandler dropHandler;

    ViewTreePanel(FileEditorManagerImpl editors, DropHandler dropHandler) {
        super(new BorderLayout());
        this.editors = editors;
        this.dropHandler = dropHandler;
        add(new JScrollPane(tree), BorderLayout.CENTER);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);
        tree.setTransferHandler(new ViewTransferHandler());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelected();
                }
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "mcs.openViewNote");
        tree.getActionMap().put("mcs.openViewNote", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelected();
            }
        });
    }

    void setView(Path vault, ViewDefinition view, Set<String> notePaths, Set<String> clonedFiles) {
        Set<List<Integer>> expanded = expandedPaths();
        List<Integer> selected = selectedEntryPath();
        model.setView(vault, view, notePaths, clonedFiles);
        expand(List.of());
        for (List<Integer> path : expanded) {
            expand(path);
        }
        if (selected != null) {
            DefaultMutableTreeNode node = find(selected);
            if (node != null) {
                TreePath path = VaultTreeModel.path(node);
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
            }
        }
    }

    JTree tree() {
        return tree;
    }

    private Set<List<Integer>> expandedPaths() {
        Set<List<Integer>> result = new HashSet<>();
        Object root = model.getRoot();
        if (!(root instanceof DefaultMutableTreeNode node)) {
            return result;
        }
        Enumeration<TreePath> paths = tree.getExpandedDescendants(VaultTreeModel.path(node));
        if (paths != null) {
            while (paths.hasMoreElements()) {
                ViewTreeModel.Node value = value(paths.nextElement());
                if (value != null) {
                    result.add(value.path());
                }
            }
        }
        return result;
    }

    private List<Integer> selectedEntryPath() {
        ViewTreeModel.Node value = value(tree.getSelectionPath());
        return value == null ? null : value.path();
    }

    private void expand(List<Integer> entryPath) {
        DefaultMutableTreeNode node = find(entryPath);
        if (node != null) {
            tree.expandPath(VaultTreeModel.path(node));
        }
    }

    private DefaultMutableTreeNode find(List<Integer> path) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        Enumeration<?> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof ViewTreeModel.Node value && value.path().equals(path)) {
                return node;
            }
        }
        return null;
    }

    private void openSelected() {
        ViewTreeModel.Node value = value(tree.getSelectionPath());
        if (value != null && !value.directory() && !value.missing()) {
            editors.openFile(value.file());
        }
    }

    private static ViewTreeModel.Node value(TreePath path) {
        if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof ViewTreeModel.Node value) {
            return value;
        }
        return null;
    }

    private final class ViewTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent component) {
            return COPY_OR_MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent component) {
            ViewTreeModel.Node value = value(tree.getSelectionPath());
            if (value == null || value.directory() || value.path().isEmpty()) {
                return null;
            }
            return new ViewTransferable(new ViewTransfer(value.viewId(), value.path(), value.entry().file()));
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return target(support) != null;
        }

        @Override
        public boolean importData(TransferSupport support) {
            ViewTreeModel.Node target = target(support);
            if (target == null) {
                return false;
            }
            try {
                ViewTransfer transfer = (ViewTransfer) support.getTransferable().getTransferData(VIEW_FLAVOR);
                boolean clone = support.getDropAction() == COPY;
                dropHandler.drop(transfer.viewId(), transfer.sourcePath(), transfer.sourceFile(), target.path(), clone);
                tree.expandPath(support.getDropLocation() instanceof JTree.DropLocation location
                        ? location.getPath() : VaultTreeModel.path((DefaultMutableTreeNode) model.getRoot()));
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
        }

        private ViewTreeModel.Node target(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(VIEW_FLAVOR)
                    || !(support.getDropLocation() instanceof JTree.DropLocation location)) {
                return null;
            }
            ViewTreeModel.Node target = value(location.getPath());
            if (target == null || !target.directory()) {
                return null;
            }
            try {
                ViewTransfer transfer = (ViewTransfer) support.getTransferable().getTransferData(VIEW_FLAVOR);
                return transfer.viewId().equals(target.viewId()) ? target : null;
            } catch (UnsupportedFlavorException | IOException e) {
                return null;
            }
        }
    }

    private static final class ViewTransferable implements Transferable {
        private final ViewTransfer value;

        private ViewTransferable(ViewTransfer value) {
            this.value = value;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{VIEW_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return VIEW_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return value;
        }
    }

    private static final class Renderer extends DefaultTreeCellRenderer {
        private final Icon noteIcon = Icons.lucide("scroll-text");
        private final Icon cloneIcon = new CloneOverlayIcon(noteIcon);

        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                              boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
            if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof ViewTreeModel.Node n) {
                if (!n.directory()) {
                    setIcon(n.cloned() ? cloneIcon : noteIcon);
                    String tooltip = n.entry().file();
                    if (!n.entry().tags().isEmpty()) {
                        tooltip += " — " + String.join(", ", n.entry().tags());
                    }
                    if (n.missing()) {
                        setText(getText() + " — " + Messages.get("vault.view.missing"));
                        setForeground(UIManager.getColor("Label.disabledForeground"));
                        tooltip += " — " + Messages.get("vault.view.missing");
                    }
                    setToolTipText(tooltip);
                } else {
                    setToolTipText(null);
                }
            }
            return this;
        }
    }
}
