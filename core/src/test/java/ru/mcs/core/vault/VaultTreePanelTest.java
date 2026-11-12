package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.i18n.Messages;

class VaultTreePanelTest {

    @TempDir
    Path tmp;

    @Test
    void automaticallyExpandedRootStillLoadsChildren() throws Exception {
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        VaultTreePanel panel = new VaultTreePanel(
                editors,
                (view, source, file, target, clone) -> { },
                directory -> { },
                (file, view, name, target) -> { },
                (file, sourceView, sourcePath, targetView, name, targetPath) -> { });
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

    @Test
    void physicalFilePopupExposesCloneAndMoveFromFiles() throws Exception {
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        VaultTreePanel panel = new VaultTreePanel(
                editors,
                (view, source, file, target, clone) -> { },
                directory -> { },
                (file, view, name, target) -> { },
                (file, sourceView, sourcePath, targetView, name, targetPath) -> { });
        VaultNote note = new VaultNote(tmp.resolve("note.md"), "note.md", "Note");

        SwingUtilities.invokeAndWait(() -> panel.setVault(tmp,
                new VaultSnapshot(List.of(note), List.of(), List.of())));
        await(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
            return root.getChildCount() == 1 && "note.md".equals(root.getChildAt(0).toString());
        });
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = popup(panel);
            assertThat(item(popup, Messages.get("vault.link.clone")).isEnabled()).isTrue();
            assertThat(item(popup, Messages.get("vault.link.move")).isEnabled()).isFalse();
        });

        ViewDefinition view = new ViewDefinition("tree", "Tree", tmp.resolve("tree.yml"), new ArrayList<>(List.of(
                ViewEntry.node("Source", null, true, List.of(), new ArrayList<>(List.of(
                        ViewEntry.file("note.md", null, true, List.of())))),
                ViewEntry.node("Target", null, true, List.of(), new ArrayList<>()))));
        SwingUtilities.invokeAndWait(() -> panel.setVault(tmp,
                new VaultSnapshot(List.of(note), List.of(view), List.of())));
        await(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
            return root.getChildCount() == 1 && "note.md".equals(root.getChildAt(0).toString());
        });
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = popup(panel);
            assertThat(item(popup, Messages.get("vault.link.clone")).isEnabled()).isTrue();
            assertThat(item(popup, Messages.get("vault.link.move")).isEnabled()).isTrue();
        });
    }

    private static JPopupMenu popup(VaultTreePanel panel) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
        DefaultMutableTreeNode note = (DefaultMutableTreeNode) root.getChildAt(0);
        return panel.physicalPopup(VaultTreeModel.path(note));
    }

    private static JMenuItem item(JPopupMenu popup, String text) {
        for (java.awt.Component component : popup.getComponents()) {
            if (component instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("Missing popup item: " + text);
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
