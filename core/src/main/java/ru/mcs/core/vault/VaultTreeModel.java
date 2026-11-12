package ru.mcs.core.vault;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Физическое дерево Markdown-файлов. Виртуальные views и ссылки-клоны добавляются следующим инкрементом. */
final class VaultTreeModel extends DefaultTreeModel {

    record Node(Path file, String title, boolean directory) {
        @Override
        public String toString() {
            return title;
        }
    }

    private static final Comparator<DefaultMutableTreeNode> ORDER = Comparator
            .comparing((DefaultMutableTreeNode n) -> !((Node) n.getUserObject()).directory())
            .thenComparing(n -> ((Node) n.getUserObject()).title(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(n -> ((Node) n.getUserObject()).title());

    private Path vault;

    VaultTreeModel() {
        super(new DefaultMutableTreeNode());
        setVault(null, List.of());
    }

    void setVault(Path root, List<VaultNote> notes) {
        vault = root == null ? null : VaultPaths.normalize(root);
        String rootTitle = vault == null ? "" : displayName(vault);
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
                vault == null ? null : new Node(vault, rootTitle, true));
        if (vault != null) {
            Map<Path, DefaultMutableTreeNode> directories = new HashMap<>();
            directories.put(vault, rootNode);
            for (VaultNote note : notes) {
                DefaultMutableTreeNode parent = rootNode;
                Path relative = vault.relativize(note.file());
                Path current = vault;
                for (int i = 0; i < relative.getNameCount(); i++) {
                    current = current.resolve(relative.getName(i).toString());
                    boolean file = i == relative.getNameCount() - 1;
                    if (file) {
                        parent.add(new DefaultMutableTreeNode(new Node(current, note.file().getFileName().toString(), false)));
                    } else {
                        DefaultMutableTreeNode directory = directories.get(current);
                        if (directory == null) {
                            directory = new DefaultMutableTreeNode(
                                    new Node(current, current.getFileName().toString(), true));
                            directories.put(current, directory);
                            parent.add(directory);
                        }
                        parent = directory;
                    }
                }
            }
            sortRecursively(rootNode);
        }
        setRoot(rootNode);
        reload();
    }

    Path vault() {
        return vault;
    }

    private static void sortRecursively(DefaultMutableTreeNode parent) {
        List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            children.add((DefaultMutableTreeNode) parent.getChildAt(i));
        }
        children.sort(ORDER);
        parent.removeAllChildren();
        for (DefaultMutableTreeNode child : children) {
            parent.add(child);
            sortRecursively(child);
        }
    }

    private static String displayName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}
