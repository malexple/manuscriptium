package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginStateTest {

    @Test
    void pluginsAreEnabledByDefaultAndOnlyDisabledIdsArePersisted() {
        PluginState state = new PluginState();
        assertThat(state.isEnabled("a")).isTrue();

        assertThat(state.setEnabled("a", false)).isTrue();
        assertThat(state.setEnabled("a", false)).isFalse();
        assertThat(state.getState()).containsEntry("disabled", List.of("a"));

        PluginState restored = new PluginState();
        restored.loadState(Map.of("disabled", List.of("a", "b")));
        assertThat(restored.isEnabled("a")).isFalse();
        assertThat(restored.isEnabled("b")).isFalse();
        assertThat(restored.isEnabled("c")).isTrue();

        assertThat(restored.setEnabled("a", true)).isTrue();
        assertThat(restored.getState()).containsEntry("disabled", List.of("b"));
    }
}
