package ru.mcs.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import ru.mcs.spi.settings.ConfigurableProvider;

/** Дерево страниц: сортировка по названию, вложенность через parentId, проверка ссылок и циклов. */
final class SettingsTree {

    private static final Comparator<ConfigurableProvider> ORDER = Comparator
            .comparing((ConfigurableProvider p) -> p.getDisplayName().toLowerCase(java.util.Locale.ROOT))
            .thenComparing(ConfigurableProvider::getId);

    private SettingsTree() {
    }

    static DefaultTreeModel create(List<ConfigurableProvider> providers) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
        for (ConfigurableProvider provider : providers) {
            if (nodes.put(provider.getId(), new DefaultMutableTreeNode(provider)) != null) {
                throw new IllegalStateException("Duplicate settings page: " + provider.getId());
            }
        }
        for (ConfigurableProvider provider : providers.stream().sorted(ORDER).toList()) {
            attach(root, nodes, nodes.get(provider.getId()), new HashSet<>());
        }
        sortChildren(root);
        return new DefaultTreeModel(root);
    }

    private static void attach(DefaultMutableTreeNode root,
                               Map<String, DefaultMutableTreeNode> nodes,
                               DefaultMutableTreeNode node,
                               Set<String> path) {
        if (node.getParent() != null) {
            return;
        }
        ConfigurableProvider provider = (ConfigurableProvider) node.getUserObject();
        if (!path.add(provider.getId())) {
            throw new IllegalStateException("Cycle in settings parents at: " + provider.getId());
        }
        String parentId = provider.getParentId();
        if (parentId == null) {
            root.add(node);
        } else {
            DefaultMutableTreeNode parent = nodes.get(parentId);
            if (parent == null) {
                throw new IllegalStateException("Unknown parent '" + parentId
                        + "' for settings page: " + provider.getId());
            }
            attach(root, nodes, parent, path);
            parent.add(node);
        }
        path.remove(provider.getId());
    }

    private static void sortChildren(DefaultMutableTreeNode node) {
        List<DefaultMutableTreeNode> children = Collections.list(node.children()).stream()
                .map(DefaultMutableTreeNode.class::cast)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        children.sort(Comparator.comparing(n -> (ConfigurableProvider) n.getUserObject(), ORDER));
        node.removeAllChildren();
        children.forEach(node::add);
        children.forEach(SettingsTree::sortChildren);
    }
}
