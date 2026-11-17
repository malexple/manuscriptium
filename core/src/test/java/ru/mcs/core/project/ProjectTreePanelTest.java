package ru.mcs.core.project;

import ru.mcs.spi.project.ViewEntry;
import ru.mcs.spi.project.ViewDefinition;
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

class ProjectTreePanelTest {

    @TempDir
    Path tmp;

    @Test
    void automaticallyExpandedRootStillLoadsChildren() throws Exception {
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        ProjectTreePanel panel = new ProjectTreePanel(
                editors,
                (view, source, file, target, clone) -> { },
                directory -> { },
                (file, view, name, target) -> { },
                (file, sourceView, sourcePath, targetView, name, targetPath) -> { });
        ProjectNote note = new ProjectNote(tmp.resolve("note.md"), "note.md", "Note");

        SwingUtilities.invokeAndWait(() -> panel.setProject(tmp,
                new ProjectSnapshot(List.of(note), List.of(), List.of())));

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
        ProjectTreePanel panel = new ProjectTreePanel(
                editors,
                (view, source, file, target, clone) -> { },
                directory -> { },
                (file, view, name, target) -> { },
                (file, sourceView, sourcePath, targetView, name, targetPath) -> { });
        ProjectNote note = new ProjectNote(tmp.resolve("note.md"), "note.md", "Note");

        SwingUtilities.invokeAndWait(() -> panel.setProject(tmp,
                new ProjectSnapshot(List.of(note), List.of(), List.of())));
        await(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
            return root.getChildCount() == 1 && "note.md".equals(root.getChildAt(0).toString());
        });
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = popup(panel);
            assertThat(item(popup, Messages.get("project.link.clone")).isEnabled()).isTrue();
            assertThat(item(popup, Messages.get("project.link.move")).isEnabled()).isFalse();
        });

        ViewDefinition view = new ViewDefinition("tree", "Tree", tmp.resolve("tree.yml"), new ArrayList<>(List.of(
                ViewEntry.node("Source", null, true, List.of(), new ArrayList<>(List.of(
                        ViewEntry.file("note.md", null, true, List.of())))),
                ViewEntry.node("Target", null, true, List.of(), new ArrayList<>()))));
        SwingUtilities.invokeAndWait(() -> panel.setProject(tmp,
                new ProjectSnapshot(List.of(note), List.of(view), List.of())));
        await(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
            return root.getChildCount() == 1 && "note.md".equals(root.getChildAt(0).toString());
        });
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = popup(panel);
            assertThat(item(popup, Messages.get("project.link.clone")).isEnabled()).isTrue();
            assertThat(item(popup, Messages.get("project.link.move")).isEnabled()).isTrue();
        });
    }

    @Test
    void physicalAndVirtualPopupsDispatchBranchStatistics() throws Exception {
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        AtomicReference<Path> physical = new AtomicReference<>();
        AtomicReference<String> viewId = new AtomicReference<>();
        AtomicReference<List<Integer>> nodePath = new AtomicReference<>();
        ViewTreePanel.StatisticsHandler statistics = new ViewTreePanel.StatisticsHandler() {
            @Override
            public void physical(Path file) {
                physical.set(file);
            }

            @Override
            public void view(String id, List<Integer> path) {
                viewId.set(id);
                nodePath.set(path);
            }
        };
        ProjectTreePanel panel = new ProjectTreePanel(
                editors,
                (view, source, file, target, clone) -> { },
                directory -> { },
                (file, view, name, target) -> { },
                (file, sourceView, sourcePath, targetView, name, targetPath) -> { },
                (view, path) -> { }, statistics);
        ProjectNote note = new ProjectNote(tmp.resolve("note.md"), "note.md", "Note");
        ViewDefinition view = new ViewDefinition("tree", "Tree", tmp.resolve("tree.yml"),
                List.of(ViewEntry.file("note.md", null, true, List.of())));

        SwingUtilities.invokeAndWait(() -> panel.setProject(tmp,
                new ProjectSnapshot(List.of(note), List.of(view), List.of())));
        await(() -> {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
            return root.getChildCount() == 1 && "note.md".equals(root.getChildAt(0).toString());
        });
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu physicalPopup = popup(panel);
            item(physicalPopup, Messages.get("branch.statistics")).doClick();
            ViewTreePanel viewPanel = (ViewTreePanel) panel.tabs().getComponentAt(1);
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) viewPanel.tree().getModel().getRoot();
            JPopupMenu viewPopup = viewPanel.popup(ProjectTreeModel.path(root));
            item(viewPopup, Messages.get("branch.statistics")).doClick();
        });

        assertThat(physical.get()).isEqualTo(note.file());
        assertThat(viewId.get()).isEqualTo("tree");
        assertThat(nodePath.get()).isEmpty();
    }

    private static JPopupMenu popup(ProjectTreePanel panel) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.physicalTree().getModel().getRoot();
        DefaultMutableTreeNode note = (DefaultMutableTreeNode) root.getChildAt(0);
        return panel.physicalPopup(ProjectTreeModel.path(note));
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
