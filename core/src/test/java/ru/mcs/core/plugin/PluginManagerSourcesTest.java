package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManagerSourcesTest {

    @TempDir
    Path system;

    @TempDir
    Path external;

    @Test
    void discoversBothSourcesAndOrdersCrossSourceDependencies() throws Exception {
        descriptor(system, "base", "base");
        descriptor(external, "consumer", "consumer", "base");
        PluginManager manager = manager();

        assertThat(manager.discover()).extracting(PluginDescriptor::id)
                .containsExactly("base", "consumer");
        manager.loadAll();

        assertThat(manager.declaredIds()).containsExactly("base", "consumer");
        assertThat(manager.loadedIds()).isEmpty();
        assertThat(manager.failures()).isEmpty();
    }

    @Test
    void duplicateIdAcrossSourcesIsRejected() throws Exception {
        descriptor(system, "same", "same");
        descriptor(external, "same", "same");
        PluginManager manager = manager();

        manager.loadAll();

        assertThat(manager.declaredIds()).isEmpty();
        assertThat(manager.failures().get("same")).isEqualTo("duplicate plugin id");
    }

    @Test
    void brokenBundledManifestDoesNotHideValidExternalPlugin() throws Exception {
        Path broken = Files.createDirectories(system.resolve("broken"));
        Files.writeString(broken.resolve("plugin.yml"), "id: [unterminated");
        descriptor(external, "good", "good");
        PluginManager manager = manager();

        manager.loadAll();

        assertThat(manager.discover()).extracting(PluginDescriptor::id)
                .containsExactly("broken", "good");
        assertThat(manager.declaredIds()).containsExactly("good");
        assertThat(manager.failures()).containsKey("broken").doesNotContainKey("good");
    }

    @Test
    void disabledBundledDependencyBlocksExternalDependent() throws Exception {
        descriptor(system, "base", "base");
        descriptor(external, "consumer", "consumer", "base");
        PluginManager manager = new PluginManager(List.of(system, external),
                d -> null, d -> { }, id -> !id.equals("base"));

        manager.loadAll();

        assertThat(manager.declaredIds()).isEmpty();
        assertThat(manager.failures()).containsKey("consumer").doesNotContainKey("base");
        assertThat(manager.failures().get("consumer")).contains("missing dependency").contains("base");
    }

    private PluginManager manager() {
        return new PluginManager(List.of(system, external), d -> null, d -> { }, id -> true);
    }

    private void descriptor(Path source, String directory, String id, String... dependencies) throws Exception {
        Path plugin = Files.createDirectories(source.resolve(directory));
        Files.writeString(plugin.resolve("plugin.yml"), """
                id: %s
                version: 1.0
                sdk-version: "0.9"
                module: %s
                depends-on: [%s]
                activation: onDemand
                """.formatted(id, id, String.join(", ", dependencies)));
    }
}
