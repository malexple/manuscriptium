package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.state.StateYaml;

class FileEditorManagerImplTest {

    @TempDir
    Path tmp;

    private FileEditorManagerImpl em;
    private FakeProvider provider;
    private Path a;
    private Path b;
    private Path c;

    @BeforeEach
    void setUp() throws IOException {
        em = new FileEditorManagerImpl();
        provider = FakeProvider.any("fake");
        em.registerProvider(provider);
        a = Files.writeString(tmp.resolve("a.txt"), "a");
        b = Files.writeString(tmp.resolve("b.txt"), "b");
        c = Files.writeString(tmp.resolve("c.txt"), "c");
    }

    private List<String> names() {
        return em.getOpenFiles().stream().map(p -> p.getFileName().toString()).toList();
    }

    @Test
    void openSelectsAndReopenSelectsExistingTab() {
        assertThat(em.openFile(a)).isTrue();
        assertThat(em.openFile(b)).isTrue();
        assertThat(em.getSelectedFile()).contains(b);

        assertThat(em.openFile(a)).isTrue();
        assertThat(em.getSelectedFile()).contains(a);
        assertThat(names()).containsExactly("a.txt", "b.txt");
        assertThat(provider.created).hasSize(2);
    }

    @Test
    void providerWithHigherPriorityWins() {
        FakeProvider special = new FakeProvider("special", 10, p -> p.toString().endsWith("b.txt"));
        em.registerProvider(special);
        em.openFile(a);
        em.openFile(b);
        assertThat(provider.created).hasSize(1);
        assertThat(special.created).hasSize(1);
    }

    @Test
    void noSuitableProviderReturnsFalse() {
        FileEditorManagerImpl empty = new FileEditorManagerImpl();
        assertThat(empty.openFile(a)).isFalse();
        assertThat(empty.getOpenFiles()).isEmpty();
    }

    @Test
    void closingSelectedTabSelectsNeighbour() {
        em.openFile(a);
        em.openFile(b);
        em.openFile(c);
        em.selectFile(b);
        em.closeFile(b);
        assertThat(em.getSelectedFile()).contains(c);
        em.closeFile(c);
        assertThat(em.getSelectedFile()).contains(a);
        em.closeFile(a);
        assertThat(em.getSelectedFile()).isEmpty();
    }

    @Test
    void closingModifiedEditorAsksConfirmer() {
        em.openFile(a);
        provider.created.get(0).setModified(true);

        em.setCloseConfirmer(e -> FileEditorManagerImpl.CloseDecision.CANCEL);
        assertThat(em.closeFile(a)).isFalse();
        assertThat(names()).containsExactly("a.txt");

        em.setCloseConfirmer(e -> FileEditorManagerImpl.CloseDecision.SAVE);
        assertThat(em.closeFile(a)).isTrue();
        assertThat(provider.created.get(0).saves).isEqualTo(1);
        assertThat(provider.created.get(0).disposed).isTrue();
        assertThat(names()).isEmpty();
    }

    @Test
    void asyncCloseWaitsForSaveBeforeDisposingEditor() {
        em.openFile(a);
        FakeEditor editor = provider.created.get(0);
        editor.setModified(true);
        editor.pendingAsyncSave = new CompletableFuture<>();
        em.setCloseConfirmer(e -> FileEditorManagerImpl.CloseDecision.SAVE);

        CompletableFuture<Boolean> closing = em.closeFileAsync(a);

        assertThat(closing).isNotDone();
        assertThat(names()).containsExactly("a.txt");
        assertThat(editor.disposed).isFalse();
        editor.pendingAsyncSave.complete(null);
        assertThat(closing.join()).isTrue();
        assertThat(editor.saves).isEqualTo(1);
        assertThat(editor.disposed).isTrue();
        assertThat(names()).isEmpty();
    }

    @Test
    void discardClosesWithoutSaving() {
        em.openFile(a);
        provider.created.get(0).setModified(true);
        em.setCloseConfirmer(e -> FileEditorManagerImpl.CloseDecision.DISCARD);
        assertThat(em.closeFile(a)).isTrue();
        assertThat(provider.created.get(0).saves).isZero();
    }

    @Test
    void failedSaveKeepsTabAndReportsError() {
        em.openFile(a);
        FakeEditor editor = provider.created.get(0);
        editor.setModified(true);
        editor.failOnSave = true;
        List<Path> reported = new ArrayList<>();
        em.setErrorReporter((p, ex) -> reported.add(p));
        em.setCloseConfirmer(e -> FileEditorManagerImpl.CloseDecision.SAVE);

        assertThat(em.closeFile(a)).isFalse();
        assertThat(reported).containsExactly(a);
        assertThat(names()).containsExactly("a.txt");
    }

    @Test
    void confirmAllStopsOnCancel() {
        em.openFile(a);
        em.openFile(b);
        provider.created.get(0).setModified(true);
        provider.created.get(1).setModified(true);

        em.setCloseConfirmer(e -> FileEditorManagerImpl.CloseDecision.CANCEL);
        assertThat(em.confirmAll()).isFalse();

        em.setCloseConfirmer(e -> FileEditorManagerImpl.CloseDecision.SAVE);
        assertThat(em.confirmAll()).isTrue();
        assertThat(em.hasModified()).isFalse();
    }

    @Test
    void pinnedTabsStayFirst() {
        em.openFile(a);
        em.openFile(b);
        em.openFile(c);

        em.setPinned(c, true);
        assertThat(names()).containsExactly("c.txt", "a.txt", "b.txt");
        em.setPinned(b, true);
        assertThat(names()).containsExactly("c.txt", "b.txt", "a.txt");
        em.setPinned(c, false);
        assertThat(names()).containsExactly("b.txt", "c.txt", "a.txt");
        assertThat(em.isPinned(b)).isTrue();
        assertThat(em.isPinned(c)).isFalse();
    }

    @Test
    void unregisterOwnerClosesItsEditorsOnly() {
        FakeProvider pluginProvider = new FakeProvider("plugin", 5, p -> p.toString().endsWith("b.txt"));
        em.registerProvider("p1", pluginProvider);
        em.openFile(a);
        em.openFile(b);
        em.selectFile(b);

        em.unregisterOwner("p1");

        assertThat(names()).containsExactly("a.txt");
        assertThat(pluginProvider.created.get(0).disposed).isTrue();
        assertThat(em.getSelectedFile()).contains(a);
        assertThat(em.providerIds()).containsExactly("fake");
    }

    @Test
    void listenersAreNotified() {
        int[] tabs = {0};
        int[] selection = {0};
        int[] state = {0};
        em.addListener(new FileEditorManagerImpl.Listener() {
            @Override
            public void tabsChanged() {
                tabs[0]++;
            }

            @Override
            public void selectionChanged() {
                selection[0]++;
            }

            @Override
            public void stateChanged() {
                state[0]++;
            }
        });
        em.openFile(a);
        provider.created.get(0).setModified(true);
        assertThat(tabs[0]).isEqualTo(1);
        assertThat(selection[0]).isEqualTo(1);
        assertThat(state[0]).isEqualTo(1);
    }

    @Test
    void sessionSurvivesPersistenceRoundTrip() {
        em.openFile(a);
        em.openFile(b);
        em.setPinned(a, true);
        em.selectFile(a);

        SessionStore saver = new SessionStore();
        saver.activate(em::currentSession);
        SessionStore loader = new SessionStore();
        loader.loadState(StateYaml.parse(StateYaml.dump(saver.getState())));

        FileEditorManagerImpl restored = new FileEditorManagerImpl();
        restored.registerProvider(FakeProvider.any("fake"));
        restored.restoreSession(loader.loaded());

        assertThat(restored.getOpenFiles()).containsExactly(a, b);
        assertThat(restored.isPinned(a)).isTrue();
        assertThat(restored.getSelectedFile()).contains(a);
    }

    @Test
    void restoreSkipsMissingFiles() throws IOException {
        em.openFile(a);
        em.openFile(b);
        SessionStore.Session session = em.currentSession();
        Files.delete(b);

        FileEditorManagerImpl restored = new FileEditorManagerImpl();
        restored.registerProvider(FakeProvider.any("fake"));
        restored.restoreSession(session);

        assertThat(restored.getOpenFiles()).containsExactly(a);
    }
}
