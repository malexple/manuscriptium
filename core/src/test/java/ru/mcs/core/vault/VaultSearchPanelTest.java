package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class VaultSearchPanelTest {

    @Test
    void rendersAsynchronousResultsAndOpensSelectedNote() throws Exception {
        List<VaultSearchResult> opened = new ArrayList<>();
        VaultSearchResult result = new VaultSearchResult(Path.of("vault/note.md"), "note.md", "Note",
                "matching snippet", 1.0f);
        VaultSearchPanel panel = panel(opened::add);
        SwingUtilities.invokeAndWait(() -> {
            panel.setBackend(query -> CompletableFuture.completedFuture(List.of(result)));
            text(panel, "vault.search.query").setText("needle");
            panel.search();
        });
        flushEdt();

        JList<?> results = component(panel, JList.class, "vault.search.results");
        assertThat(results.getModel().getSize()).isEqualTo(1);
        assertThat(results.getModel().getElementAt(0)).isEqualTo(result);
        assertThat(component(panel, JLabel.class, "vault.search.status").getText()).contains("1 results");
        SwingUtilities.invokeAndWait(() -> results.getActionMap().get("mcs.openSearchResult")
                .actionPerformed(new java.awt.event.ActionEvent(results, 0, "open")));
        assertThat(opened).containsExactly(result);
    }

    @Test
    void ignoresResultFromPreviousVault() throws Exception {
        VaultSearchPanel panel = panel(ignored -> { });
        CompletableFuture<List<VaultSearchResult>> oldSearch = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.setBackend(query -> oldSearch);
            text(panel, "vault.search.query").setText("old");
            panel.search();
            panel.setBackend(null);
        });
        oldSearch.complete(List.of(result("old.md")));
        flushEdt();

        assertThat(component(panel, JList.class, "vault.search.results").getModel().getSize()).isZero();
        assertThat(component(panel, JLabel.class, "vault.search.status").getText())
                .contains("Open a vault");
    }

    @Test
    void ignoresOlderQueryCompletedAfterNewerQuery() throws Exception {
        VaultSearchPanel panel = panel(ignored -> { });
        CompletableFuture<List<VaultSearchResult>> first = new CompletableFuture<>();
        CompletableFuture<List<VaultSearchResult>> second = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            panel.setBackend(query -> calls.getAndIncrement() == 0 ? first : second);
            text(panel, "vault.search.query").setText("first");
            panel.search();
            text(panel, "vault.search.query").setText("second");
            panel.search();
        });
        second.complete(List.of(result("second.md")));
        flushEdt();
        first.complete(List.of(result("first.md")));
        flushEdt();

        JList<?> results = component(panel, JList.class, "vault.search.results");
        assertThat(results.getModel().getSize()).isEqualTo(1);
        assertThat(results.getModel().getElementAt(0)).extracting("path").isEqualTo("second.md");
    }

    @Test
    void invalidNewQueryReenablesButtonAndStillIgnoresRunningSearch() throws Exception {
        VaultSearchPanel panel = panel(ignored -> { });
        CompletableFuture<List<VaultSearchResult>> running = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.setBackend(query -> running);
            text(panel, "vault.search.query").setText("first");
            panel.search();
            text(panel, "vault.search.subtree").setText("../outside");
            panel.search();
        });
        running.complete(List.of(result("stale.md")));
        flushEdt();

        assertThat(component(panel, javax.swing.JButton.class, "vault.search.submit").isEnabled()).isTrue();
        assertThat(component(panel, JList.class, "vault.search.results").getModel().getSize()).isZero();
        assertThat(component(panel, JLabel.class, "vault.search.status").getText()).contains("Invalid search");
    }

    @Test
    void rejectsUnsafeSubtreeWithoutCallingBackend() throws Exception {
        VaultSearchPanel panel = panel(ignored -> { });
        AtomicInteger calls = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            panel.setBackend(query -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(List.of());
            });
            text(panel, "vault.search.subtree").setText("../outside");
            panel.search();
        });
        assertThat(calls.get()).isZero();
        assertThat(component(panel, JLabel.class, "vault.search.status").getText()).contains("Invalid search");
    }

    @Test
    void supportsFilterOnlySearchAndReportsFailures() throws Exception {
        VaultSearchPanel panel = panel(ignored -> { });
        AtomicReference<VaultSearchQuery> captured = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.setBackend(query -> {
                captured.set(query);
                return CompletableFuture.failedFuture(new java.io.IOException("index unavailable"));
            });
            text(panel, "vault.search.subtree").setText("notes/java");
            combo(panel, "vault.search.draft").setSelectedIndex(1);
        });
        flushEdt();

        assertThat(captured.get().text()).isEmpty();
        assertThat(captured.get().subtree()).isEqualTo("notes/java");
        assertThat(captured.get().draft()).isTrue();
        assertThat(component(panel, JLabel.class, "vault.search.status").getText())
                .contains("index unavailable");
    }

    private static VaultSearchPanel panel(java.util.function.Consumer<VaultSearchResult> opener) throws Exception {
        AtomicReference<VaultSearchPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new VaultSearchPanel(opener)));
        return reference.get();
    }

    private static VaultSearchResult result(String path) {
        return new VaultSearchResult(Path.of("vault").resolve(path), path, path, "snippet", 1.0f);
    }

    private static JTextField text(VaultSearchPanel panel, String name) {
        return component(panel, JTextField.class, name);
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Object> combo(VaultSearchPanel panel, String name) {
        return (JComboBox<Object>) component(panel, JComboBox.class, name);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static <T extends Component> T component(Container root, Class<T> type, String name) {
        T result = find(root, type, name);
        assertThat(result).as("component %s of %s", name, type.getSimpleName()).isNotNull();
        return result;
    }

    private static <T extends Component> T find(Container root, Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T result = find(container, type, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
