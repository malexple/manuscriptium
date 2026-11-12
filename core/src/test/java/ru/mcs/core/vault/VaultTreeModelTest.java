package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultTreeModelTest {

    @TempDir
    Path tmp;

    @Test
    void materializesSortedPhysicalHierarchyOnlyOnExpansion() {
        VaultTreeModel model = new VaultTreeModel();
        List<VaultNote> notes = List.of(
                note("z.md"), note("beta/b.md"), note("alpha/nested/a.md"), note("alpha/first.md"));

        model.setVault(tmp, notes);
        long generation = model.generation();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getChildCount()).isEqualTo(1);
        assertThat(root.getUserObject()).isInstanceOf(VaultTreeModel.Node.class);
        assertThat(model.isLoaded(tmp)).isFalse();

        List<VaultTreeModel.Node> rootChildren = model.children(tmp, generation);
        assertThat(rootChildren).extracting(VaultTreeModel.Node::title)
                .containsExactly("alpha", "beta", "z.md");
        assertThat(model.installChildren(tmp, generation, rootChildren)).isTrue();
        assertThat(root.getChildCount()).isEqualTo(3);
        assertThat(root.getChildAt(0).toString()).isEqualTo("alpha");
        assertThat(root.getChildAt(1).toString()).isEqualTo("beta");
        assertThat(root.getChildAt(2).toString()).isEqualTo("z.md");

        Path alphaPath = tmp.resolve("alpha");
        assertThat(model.children(alphaPath, generation)).extracting(VaultTreeModel.Node::title)
                .containsExactly("nested", "first.md");
        DefaultMutableTreeNode alpha = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(alpha.getChildCount()).isEqualTo(1);
        assertThat(alpha.getUserObject()).isInstanceOf(VaultTreeModel.Node.class);
        assertThat(model.installChildren(alphaPath, generation, model.children(alphaPath, generation))).isTrue();
        assertThat(alpha.getChildAt(0).toString()).isEqualTo("nested");
        assertThat(alpha.getChildAt(1).toString()).isEqualTo("first.md");
    }

    @Test
    void newSnapshotInvalidatesPendingWorkerAndDoesNotDuplicateNodes() {
        VaultTreeModel model = new VaultTreeModel();
        model.setVault(tmp, List.of(note("a.md")));
        long stale = model.generation();
        List<VaultTreeModel.Node> before = model.children(tmp, stale);

        model.setVault(tmp, List.of(note("a.md"), note("b.md")));

        assertThat(model.installChildren(tmp, stale, before)).isFalse();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getChildCount()).isEqualTo(1);
        long current = model.generation();
        model.installChildren(tmp, current, model.children(tmp, current));
        assertThat(root.getChildCount()).isEqualTo(2);
    }

    @Test
    void emptyVaultHasRootAndLoadsNoChildren() {
        VaultTreeModel model = new VaultTreeModel();
        model.setVault(tmp, List.of());
        long generation = model.generation();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getUserObject()).isInstanceOf(VaultTreeModel.Node.class);
        assertThat(model.children(tmp, generation)).isEmpty();
        assertThat(model.installChildren(tmp, generation, List.of())).isTrue();
        assertThat(root.getChildCount()).isZero();
        assertThat(model.vault()).isEqualTo(tmp);
    }

    private VaultNote note(String relative) {
        return new VaultNote(tmp.resolve(relative), relative, relative);
    }
}
