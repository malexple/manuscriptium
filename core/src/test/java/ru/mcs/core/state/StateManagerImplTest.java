package ru.mcs.core.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.service.PersistentStateComponent;
import ru.mcs.spi.service.Scope;

class StateManagerImplTest {

    private static final class MapState implements PersistentStateComponent {
        Map<String, Object> data = new LinkedHashMap<>();
        int loads;

        @Override
        public Map<String, Object> getState() {
            return new LinkedHashMap<>(data);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            loads++;
            data = new LinkedHashMap<>(state);
        }
    }

    @TempDir
    Path tmp;

    private Path appDir;
    private Path vaultDir;
    private StateManagerImpl manager;

    @BeforeEach
    void setUp() {
        appDir = tmp.resolve("app");
        vaultDir = tmp.resolve("vault");
        manager = new StateManagerImpl(appDir, vaultDir, true);
    }

    @Test
    void loadsExistingStateOnRegister() throws IOException {
        Files.createDirectories(appDir);
        Files.writeString(appDir.resolve("x.yml"), "a: 1\nb: [p, q]\n");
        MapState state = new MapState();
        manager.register("core", "x", Scope.APPLICATION, state);
        assertThat(state.loads).isEqualTo(1);
        assertThat(state.data.get("a")).isEqualTo(1);
    }

    @Test
    void savesOnlyWhenStateChanged() throws IOException {
        MapState state = new MapState();
        manager.register("core", "x", Scope.APPLICATION, state);

        manager.saveChanged();
        assertThat(appDir.resolve("x.yml")).doesNotExist();

        state.data.put("a", 1);
        manager.saveChanged();
        assertThat(Files.readString(appDir.resolve("x.yml"))).contains("a: 1");

        Files.delete(appDir.resolve("x.yml"));
        manager.saveChanged();
        assertThat(appDir.resolve("x.yml")).as("unchanged state is not rewritten").doesNotExist();

        state.data.put("a", 2);
        manager.saveChanged();
        assertThat(Files.readString(appDir.resolve("x.yml"))).contains("a: 2");
    }

    @Test
    void unregisterSavesFinalState() throws IOException {
        MapState state = new MapState();
        manager.register("p", "p.x", Scope.APPLICATION, state);
        state.data.put("k", "v");
        manager.unregister("p", "p.x");
        assertThat(Files.readString(appDir.resolve("p.x.yml"))).contains("k: v");
        assertThat(manager.ids()).isEmpty();
    }

    @Test
    void readOnlyManagerNeverWrites() {
        StateManagerImpl readOnly = new StateManagerImpl(appDir, vaultDir, false);
        MapState state = new MapState();
        readOnly.register("core", "x", Scope.APPLICATION, state);
        state.data.put("a", 1);
        readOnly.saveChanged();
        assertThat(appDir).doesNotExist();
    }

    @Test
    void invalidAndDuplicateIdsAreRejected() {
        MapState state = new MapState();
        for (String bad : new String[]{"", "a/b", "..", "a b", "../x"}) {
            assertThatThrownBy(() -> manager.register("core", bad, Scope.APPLICATION, state))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        manager.register("core", "ok", Scope.APPLICATION, state);
        assertThatThrownBy(() -> manager.register("core", "ok", Scope.APPLICATION, new MapState()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopedManagerPrefixesIdsAndCleansUpByOwner() throws IOException {
        MapState state = new MapState();
        manager.scopedFor("ru.p").register("settings", Scope.APPLICATION, state);
        assertThat(manager.ids()).containsExactly("ru.p.settings");

        state.data.put("a", 1);
        manager.unregisterOwner("ru.p");

        assertThat(manager.ids()).isEmpty();
        assertThat(Files.readString(appDir.resolve("ru.p.settings.yml"))).contains("a: 1");
    }

    @Test
    void vaultScopeUsesVaultDirectory() {
        MapState state = new MapState();
        manager.register("core", "v", Scope.VAULT, state);
        state.data.put("a", 1);
        manager.saveChanged();
        assertThat(vaultDir.resolve("v.yml")).exists();
        assertThat(appDir.resolve("v.yml")).doesNotExist();
    }

    @Test
    void corruptFileDoesNotBreakRegistration() throws IOException {
        Files.createDirectories(appDir);
        Files.writeString(appDir.resolve("x.yml"), "[: broken");
        MapState state = new MapState();
        manager.register("core", "x", Scope.APPLICATION, state);
        assertThat(state.loads).isZero();
        assertThat(manager.ids()).containsExactly("x");
    }

    @Test
    void failingOrUnserialisableComponentsAreSkipped() {
        PersistentStateComponent failing = new PersistentStateComponent() {
            @Override
            public Map<String, Object> getState() {
                throw new IllegalStateException("boom");
            }

            @Override
            public void loadState(Map<String, Object> state) {
            }
        };
        MapState weird = new MapState();
        weird.data.put("o", new Object());
        MapState good = new MapState();
        good.data.put("a", 1);

        manager.register("core", "failing", Scope.APPLICATION, failing);
        manager.register("core", "weird", Scope.APPLICATION, weird);
        manager.register("core", "good", Scope.APPLICATION, good);
        manager.saveChanged();

        assertThat(appDir.resolve("good.yml")).exists();
        assertThat(appDir.resolve("weird.yml")).doesNotExist();
        assertThat(appDir.resolve("failing.yml")).doesNotExist();
    }
}
