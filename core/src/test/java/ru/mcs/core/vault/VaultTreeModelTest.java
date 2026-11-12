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
    void buildsSortedPhysicalHierarchyWithoutDuplicates() {
        VaultTreeModel model = new VaultTreeModel();
        List<VaultNote> notes = List.of(
                note("z.md"), note("beta/b.md"), note("alpha/nested/a.md"), note("alpha/first.md"));

        model.setVault(tmp, notes);
        model.setVault(tmp, notes);

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getChildCount()).isEqualTo(3);
        assertThat(root.getChildAt(0).toString()).isEqualTo("alpha");
        assertThat(root.getChildAt(1).toString()).isEqualTo("beta");
        assertThat(root.getChildAt(2).toString()).isEqualTo("z.md");
        DefaultMutableTreeNode alpha = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(alpha.getChildAt(0).toString()).isEqualTo("nested");
        assertThat(alpha.getChildAt(1).toString()).isEqualTo("first.md");
        assertThat(((DefaultMutableTreeNode) alpha.getChildAt(0)).getChildAt(0).toString()).isEqualTo("a.md");
    }

    @Test
    void emptyVaultHasRootButNoChildren() {
        VaultTreeModel model = new VaultTreeModel();
        model.setVault(tmp, List.of());
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getUserObject()).isInstanceOf(VaultTreeModel.Node.class);
        assertThat(root.getChildCount()).isZero();
        assertThat(model.vault()).isEqualTo(tmp);
    }

    private VaultNote note(String relative) {
        return new VaultNote(tmp.resolve(relative), relative, relative);
    }
}
