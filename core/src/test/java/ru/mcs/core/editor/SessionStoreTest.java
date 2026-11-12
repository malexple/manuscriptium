package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.mcs.core.state.StateYaml;

class SessionStoreTest {

    private static final Path A = Path.of("a.txt").toAbsolutePath();
    private static final Path B = Path.of("b.txt").toAbsolutePath();

    @Test
    void roundTripThroughYaml() {
        SessionStore first = new SessionStore();
        first.activate(() -> new SessionStore.Session(
                List.of(new SessionStore.Tab(A, true), new SessionStore.Tab(B, false)), B));

        SessionStore second = new SessionStore();
        second.loadState(StateYaml.parse(StateYaml.dump(first.getState())));

        assertThat(second.loaded().tabs()).containsExactly(new SessionStore.Tab(A, true), new SessionStore.Tab(B, false));
        assertThat(second.loaded().selected()).isEqualTo(B);
    }

    @Test
    void beforeActivationLoadedStateIsReturned() {
        SessionStore store = new SessionStore();
        store.loadState(Map.of("tabs", List.of(Map.of("file", A.toString(), "pinned", false))));
        @SuppressWarnings("unchecked")
        List<Object> tabs = (List<Object>) store.getState().get("tabs");
        assertThat(tabs).hasSize(1);
    }

    @Test
    void afterActivationLiveStateWins() {
        SessionStore store = new SessionStore();
        store.loadState(Map.of("tabs", List.of(Map.of("file", A.toString(), "pinned", false))));
        store.activate(() -> new SessionStore.Session(List.of(), null));
        assertThat((List<?>) store.getState().get("tabs")).isEmpty();
        assertThat(store.getState()).doesNotContainKey("selected");
    }

    @Test
    void deactivationRestoresLoadedStateDuringVaultSwitch() {
        SessionStore store = new SessionStore();
        store.loadState(Map.of("tabs", List.of(Map.of("file", A.toString(), "pinned", true))));
        store.activate(() -> new SessionStore.Session(List.of(), null));
        assertThat((List<?>) store.getState().get("tabs")).isEmpty();

        store.deactivate();
        assertThat((List<?>) store.getState().get("tabs")).hasSize(1);
    }

    @Test
    void garbageEntriesAreSkipped() {
        SessionStore store = new SessionStore();
        store.loadState(Map.of("tabs", List.of("not a map", Map.of("pinned", true), Map.of("file", 5),
                Map.of("file", A.toString())), "selected", 42));
        assertThat(store.loaded().tabs()).containsExactly(new SessionStore.Tab(A, false));
        assertThat(store.loaded().selected()).isNull();
    }
}
