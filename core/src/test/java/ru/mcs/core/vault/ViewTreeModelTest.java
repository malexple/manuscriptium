package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViewTreeModelTest {

    @TempDir
    Path tmp;

    @Test
    void projectsVisibilityPathsTitlesMissingFilesAndCloneBadges() {
        ViewEntry shared = ViewEntry.file("shared.md", "Shared title", true, List.of("tag"));
        ViewEntry missing = ViewEntry.file("missing.md", null, true, List.of());
        ViewEntry branch = ViewEntry.node("Branch", "Override branch", true, List.of(),
                new ArrayList<>(List.of(shared, missing)));
        ViewEntry hiddenBranch = ViewEntry.node("Hidden", null, false, List.of(),
                new ArrayList<>(List.of(ViewEntry.file("hidden.md", null, true, List.of()))));
        ViewDefinition view = new ViewDefinition("id", "View name", tmp.resolve("id.yml"),
                new ArrayList<>(List.of(branch, hiddenBranch, shared,
                        ViewEntry.file("invisible.md", null, false, List.of()))));
        ViewTreeModel model = new ViewTreeModel();

        model.setView(tmp, view, Set.of("shared.md"), Set.of("shared.md"));

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.toString()).isEqualTo("View name");
        assertThat(root.getChildCount()).isEqualTo(2);
        DefaultMutableTreeNode branchNode = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(branchNode.toString()).isEqualTo("Override branch");
        assertThat(branchNode.getChildCount()).isEqualTo(2);
        ViewTreeModel.Node first = (ViewTreeModel.Node) ((DefaultMutableTreeNode) branchNode.getChildAt(0))
                .getUserObject();
        assertThat(first.path()).containsExactly(0, 0);
        assertThat(first.title()).isEqualTo("Shared title");
        assertThat(first.file()).isEqualTo(tmp.resolve("shared.md"));
        assertThat(first.missing()).isFalse();
        assertThat(first.cloned()).isTrue();
        ViewTreeModel.Node second = (ViewTreeModel.Node) ((DefaultMutableTreeNode) branchNode.getChildAt(1))
                .getUserObject();
        assertThat(second.path()).containsExactly(0, 1);
        assertThat(second.missing()).isTrue();
        ViewTreeModel.Node secondShared = (ViewTreeModel.Node) ((DefaultMutableTreeNode) root.getChildAt(1))
                .getUserObject();
        assertThat(secondShared.path()).containsExactly(2);
        assertThat(secondShared.cloned()).isTrue();
    }
}
