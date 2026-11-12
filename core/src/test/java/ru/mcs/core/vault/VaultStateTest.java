package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultStateTest {

    @TempDir
    Path tmp;

    @Test
    void roundTripsLastVaultAndClearsInvalidState() {
        VaultState state = new VaultState();
        state.setVault(tmp.resolve("vault"));
        Map<String, Object> saved = state.getState();

        VaultState loaded = new VaultState();
        loaded.loadState(saved);
        assertThat(loaded.vault()).contains(tmp.resolve("vault").toAbsolutePath().normalize());

        loaded.loadState(Map.of("last", ""));
        assertThat(loaded.vault()).isEmpty();
    }
}
