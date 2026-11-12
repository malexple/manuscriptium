package ru.mcs.core.vault;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;

/** Панель Vault: двойной щелчок или Enter открывает Markdown в существующей Editor area. */
final class VaultTreePanel extends JPanel {

    private static final String EMPTY = "empty";
    private static final String TREE = "tree";

    private final CardLayout cards = new CardLayout();
    private final VaultTreeModel model = new VaultTreeModel();
    private final JTree tree = new JTree(model);
    private final FileEditorManagerImpl editors;

    VaultTreePanel(FileEditorManagerImpl editors) {
        super(new BorderLayout());
        this.editors = editors;
        setLayout(cards);
        JLabel empty = new JLabel(Messages.get("vault.empty"), SwingConstants.CENTER);
        empty.setBorder(new EmptyBorder(16, 16, 16, 16));
        add(empty, EMPTY);
        add(new JScrollPane(tree), TREE);
        cards.show(this, EMPTY);

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelected();
                }
            }
        });
        InputMap input = tree.getInputMap();
        ActionMap actions = tree.getActionMap();
        input.put(KeyStroke.getKeyStroke("ENTER"), "mcs.openVaultNote");
        actions.put("mcs.openVaultNote", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelected();
            }
        });
    }

    void setVault(Path root, List<VaultNote> notes) {
        Set<Path> expanded = expandedFiles();
        Path selected = selectedFile();
        model.setVault(root, notes);
        if (root == null) {
            cards.show(this, EMPTY);
            return;
        }
        expand(root);
        for (Path file : expanded) {
            expand(file);
        }
        if (selected != null) {
            select(selected);
        }
        cards.show(this, TREE);
    }

    private Set<Path> expandedFiles() {
        Set<Path> result = new HashSet<>();
        Object root = model.getRoot();
        if (!(root instanceof DefaultMutableTreeNode node)) {
            return result;
        }
        Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(node));
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
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        Enumeration<?> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof VaultTreeModel.Node n && n.directory() && n.file().equals(file)) {
                tree.expandPath(new TreePath(node.getPath()));
                return;
            }
        }
    }

    private void select(Path file) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        Enumeration<?> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof VaultTreeModel.Node n && n.file().equals(file)) {
                TreePath path = new TreePath(node.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    private Path selectedFile() {
        return file(tree.getSelectionPath());
    }

    private static Path file(TreePath path) {
        if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)
                || !(node.getUserObject() instanceof VaultTreeModel.Node value)) {
            return null;
        }
        return value.file();
    }

    private void openSelected() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        if (root.getUserObject() == null) {
            return;
        }
        TreePath path = tree.getSelectionPath();
        if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof VaultTreeModel.Node value && !value.directory()) {
            editors.openFile(value.file());
        }
    }

    private static final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                              boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
            if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof VaultTreeModel.Node n && !n.directory()) {
                setIcon(Icons.lucide("scroll-text"));
            }
            return this;
        }
    }
}
