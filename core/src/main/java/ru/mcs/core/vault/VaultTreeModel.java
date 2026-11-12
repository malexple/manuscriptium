package ru.mcs.core.vault;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Ленивая модель физического дерева: снимок индекса хранится отдельно, Swing-узлы создаются при раскрытии. */
final class VaultTreeModel extends DefaultTreeModel {

    record Node(Path file, String title, boolean directory) {
        @Override
        public String toString() {
            return title;
        }
    }

    private record Hierarchy(Path vault, Map<Path, List<Node>> children) {
    }

    private static final Comparator<Node> ORDER = Comparator
            .comparing((Node node) -> !node.directory())
            .thenComparing(Node::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Node::title);

    private volatile Hierarchy hierarchy = new Hierarchy(null, Map.of());
    private final Set<Path> loaded = new HashSet<>();
    private volatile long generation;

    VaultTreeModel() {
        super(new DefaultMutableTreeNode());
        setVault(null, List.of());
    }

    void setVault(Path root, List<VaultNote> notes) {
        Path vault = root == null ? null : VaultPaths.normalize(root);
        hierarchy = buildHierarchy(vault, notes);
        loaded.clear();
        generation++;
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
                vault == null ? null : new Node(vault, displayName(vault), true));
        if (vault != null) {
            rootNode.add(new DefaultMutableTreeNode());
        }
        setRoot(rootNode);
        reload();
    }

    Path vault() {
        return hierarchy.vault();
    }

    long generation() {
        return generation;
    }

    boolean isLoaded(Path directory) {
        return loaded.contains(directory);
    }

    /** Вызывается в SwingWorker; immutable-снимок можно безопасно читать при параллельной смене поколения. */
    List<Node> children(Path directory, long expectedGeneration) {
        Hierarchy current = hierarchy;
        if (current.vault() == null || generation != expectedGeneration) {
            return List.of();
        }
        return current.children().getOrDefault(directory, List.of());
    }

    /** Устанавливает результат только если во время фоновой работы снимок не сменился. Вызывать на EDT. */
    boolean installChildren(Path directory, long expectedGeneration, List<Node> children) {
        if (generation != expectedGeneration) {
            return false;
        }
        DefaultMutableTreeNode parent = findNode(directory);
        if (parent == null) {
            return false;
        }
        parent.removeAllChildren();
        for (Node child : children) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(child);
            if (child.directory()) {
                node.add(new DefaultMutableTreeNode());
            }
            parent.add(node);
        }
        loaded.add(directory);
        nodeStructureChanged(parent);
        return true;
    }

    DefaultMutableTreeNode findNode(Path file) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
        if (root.getUserObject() instanceof Node node && node.file().equals(file)) {
            return root;
        }
        var nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof Node value && value.file().equals(file)) {
                return node;
            }
        }
        return null;
    }

    static TreePath path(DefaultMutableTreeNode node) {
        return new TreePath(node.getPath());
    }

    private static Hierarchy buildHierarchy(Path vault, List<VaultNote> notes) {
        if (vault == null) {
            return new Hierarchy(null, Map.of());
        }
        Map<Path, List<Node>> children = new HashMap<>();
        Map<Path, Node> directories = new HashMap<>();
        children.put(vault, new ArrayList<>());
        directories.put(vault, new Node(vault, displayName(vault), true));
        for (VaultNote note : notes) {
            Path relative = vault.relativize(note.file());
            Path parent = vault;
            for (int i = 0; i < relative.getNameCount() - 1; i++) {
                Path directory = parent.resolve(relative.getName(i).toString());
                if (!directories.containsKey(directory)) {
                    Node node = new Node(directory, directory.getFileName().toString(), true);
                    directories.put(directory, node);
                    children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
                    children.put(directory, new ArrayList<>());
                }
                parent = directory;
            }
            children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(
                    new Node(note.file(), note.file().getFileName().toString(), false));
        }
        Map<Path, List<Node>> immutable = new HashMap<>();
        children.forEach((path, nodes) -> {
            nodes.sort(ORDER);
            immutable.put(path, List.copyOf(nodes));
        });
        return new Hierarchy(vault, Map.copyOf(immutable));
    }

    private static String displayName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}
