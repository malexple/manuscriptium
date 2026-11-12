package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.editor.FileEditorManagerImpl;

class VaultTreePanelTest {

    @TempDir
    Path tmp;

    @Test
    void automaticallyExpandedRootStillLoadsChildren() throws Exception {
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        VaultTreePanel panel = new VaultTreePanel(editors, (view, source, file, target, clone) -> { });
        VaultNote note = new VaultNote(tmp.resolve("note.md"), "note.md", "Note");

        SwingUtilities.invokeAndWait(() -> panel.setVault(tmp,
                new VaultSnapshot(List.of(note), List.of(), List.of())));

        await(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
            return root.getChildCount() == 1 && "note.md".equals(root.getChildAt(0).toString());
        });
        SwingUtilities.invokeAndWait(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
            assertThat(root.getChildAt(0).toString()).isEqualTo("note.md");
        });
    }

    private static void await(BooleanSupplier condition) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    if (condition.getAsBoolean()) {
                        failure.set(null);
                    } else {
                        failure.set(new AssertionError("condition is false"));
                    }
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            if (failure.get() == null) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Swing tree was not materialized before timeout", failure.get());
    }
}
