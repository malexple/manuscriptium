package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManagerEnabledTest {

    @TempDir
    Path temp;

    @Test
    void discoverySeesAllButOnlyEnabledPluginsDeclareContributions() throws Exception {
        descriptor("enabled");
        descriptor("disabled");
        descriptor(".staging-ignored");
        PluginState state = new PluginState();
        state.setEnabled("disabled", false);
        PluginManager manager = new PluginManager(temp, d -> null, d -> { }, state::isEnabled);

        assertThat(manager.discover()).extracting(PluginDescriptor::id)
                .containsExactly("disabled", "enabled");
        manager.loadAll();

        assertThat(manager.declaredIds()).containsExactly("enabled");
        assertThat(manager.loadedIds()).isEmpty();
        assertThat(manager.failures()).isEmpty();
    }

    private void descriptor(String id) throws Exception {
        Path directory = Files.createDirectories(temp.resolve(id));
        Files.writeString(directory.resolve("plugin.yml"), """
                id: %s
                version: 1.0
                sdk-version: "0.9"
                module: %s
                activation: onDemand
                """.formatted(id, id));
    }
}
