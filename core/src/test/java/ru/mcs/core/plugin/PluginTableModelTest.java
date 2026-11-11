package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginTableModelTest {

    @Test
    void checkboxChangesAreStagedThenAppliedOrReset() {
        PluginState state = new PluginState();
        PluginTableModel model = new PluginTableModel(state);
        PluginDescriptor descriptor = new PluginDescriptor("demo", "Demo", "1.0", "0.9", "demo",
                List.of(), List.of(), Path.of("/demo"));
        model.refresh(List.of(descriptor), Set.of("demo"), java.util.Map.of());

        assertThat(model.getValueAt(0, 0)).isEqualTo(true);
        assertThat(model.isModified()).isFalse();

        model.setValueAt(false, 0, 0);
        assertThat(model.isModified()).isTrue();
        assertThat(state.isEnabled("demo")).isTrue();
        assertThat(model.getValueAt(0, 5)).isEqualTo("Restart required");

        assertThat(model.applyChanges()).isTrue();
        assertThat(state.isEnabled("demo")).isFalse();
        assertThat(model.isModified()).isFalse();
        assertThat(model.getValueAt(0, 5)).isEqualTo("Disabled");

        model.setValueAt(true, 0, 0);
        assertThat(model.isModified()).isTrue();
        model.resetChanges();
        assertThat(model.getValueAt(0, 0)).isEqualTo(false);
        assertThat(model.isModified()).isFalse();
        assertThat(model.applyChanges()).isFalse();
    }

    @Test
    void incompatibleSdkIsVisibleInRegistry() {
        PluginState state = new PluginState();
        PluginTableModel model = new PluginTableModel(state);
        PluginDescriptor descriptor = new PluginDescriptor("old", "Old", "1.0", "0.8", "old",
                List.of(), List.of(), Path.of("/old"));
        model.refresh(List.of(descriptor), Set.of(), java.util.Map.of());

        assertThat(model.getValueAt(0, 5)).isEqualTo("Incompatible SDK");
    }
}
