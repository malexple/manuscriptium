package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurableProvider;

class SettingsTreeTest {

    @Test
    void buildsParentLinksAndSortsEveryLevelByDisplayName() {
        DefaultMutableTreeNode root = rootOf(SettingsTree.create(List.of(
                provider("z", "Zulu", null),
                provider("z.b", "Beta", "z"),
                provider("a", "Alpha", null),
                provider("z.a", "Alpha", "z"))));

        assertThat(childIds(root)).containsExactly("a", "z");
        DefaultMutableTreeNode zulu = (DefaultMutableTreeNode) root.getLastChild();
        assertThat(childIds(zulu)).containsExactly("z.a", "z.b");
    }

    @Test
    void rejectsUnknownParent() {
        assertThatThrownBy(() -> SettingsTree.create(List.of(provider("p", "Page", "missing"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown parent");
    }

    @Test
    void rejectsParentCycles() {
        assertThatThrownBy(() -> SettingsTree.create(List.of(
                provider("a", "A", "b"),
                provider("b", "B", "a"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cycle");
    }

    @Test
    void rejectsDuplicateIds() {
        assertThatThrownBy(() -> SettingsTree.create(List.of(
                provider("a", "A", null),
                provider("a", "Another A", null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    private static DefaultMutableTreeNode rootOf(javax.swing.tree.TreeModel model) {
        return (DefaultMutableTreeNode) model.getRoot();
    }

    private static List<String> childIds(DefaultMutableTreeNode node) {
        return Collections.list(node.children()).stream()
                .map(DefaultMutableTreeNode.class::cast)
                .map(child -> ((ConfigurableProvider) child.getUserObject()).getId())
                .toList();
    }

    private static ConfigurableProvider provider(String id, String title, String parentId) {
        return new ConfigurableProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return title;
            }

            @Override
            public String getParentId() {
                return parentId;
            }

            @Override
            public Configurable createConfigurable() {
                return null;
            }
        };
    }
}
