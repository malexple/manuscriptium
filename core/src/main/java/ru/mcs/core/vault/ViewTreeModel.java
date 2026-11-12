package ru.mcs.core.vault;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Видимая проекция одного YAML-view. Скрытые записи вместе с потомками не попадают в Swing-дерево. */
final class ViewTreeModel extends DefaultTreeModel {

    record Node(String viewId, ViewEntry entry, List<Integer> path, Path file,
                boolean missing, boolean cloned) {
        Node {
            path = List.copyOf(path);
        }

        boolean directory() {
            return entry == null || entry.isNode();
        }

        String title() {
            return entry == null ? viewId : entry.title();
        }

        @Override
        public String toString() {
            return title();
        }
    }

    private ViewDefinition view;

    ViewTreeModel() {
        super(new DefaultMutableTreeNode());
    }

    void setView(Path vault, ViewDefinition definition, Set<String> notePaths, Set<String> clonedFiles) {
        this.view = definition;
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Node(
                definition.id(), ViewEntry.node(definition.name(), null, true, List.of(), List.of()),
                List.of(), null, false, false));
        addChildren(root, vault, definition.entries(), List.of(), notePaths, clonedFiles);
        setRoot(root);
        reload();
    }

    ViewDefinition view() {
        return view;
    }

    private static void addChildren(DefaultMutableTreeNode parent, Path vault, List<ViewEntry> entries,
                                    List<Integer> parentPath, Set<String> notePaths, Set<String> clonedFiles) {
        for (int i = 0; i < entries.size(); i++) {
            ViewEntry entry = entries.get(i);
            if (!entry.visibility()) {
                continue;
            }
            List<Integer> path = new ArrayList<>(parentPath);
            path.add(i);
            Path file = entry.isNode() ? null : vault.resolve(entry.file()).normalize();
            Node node = new Node(parent.getUserObject() instanceof Node p ? p.viewId() : "", entry, path,
                    file, !entry.isNode() && !notePaths.contains(entry.file()),
                    !entry.isNode() && clonedFiles.contains(entry.file()));
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(node);
            parent.add(child);
            if (entry.isNode()) {
                addChildren(child, vault, entry.children(), path, notePaths, clonedFiles);
            }
        }
    }
}
