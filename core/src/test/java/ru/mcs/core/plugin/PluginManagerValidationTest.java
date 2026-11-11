package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManagerValidationTest {

    @TempDir
    Path temp;

    @Test
    void incompatibleOnDemandPluginIsRejectedBeforeContributionsAreDeclared() throws Exception {
        descriptor("old", "old", "0.8");
        PluginManager manager = manager();

        manager.loadAll();

        assertThat(manager.declaredIds()).isEmpty();
        assertThat(manager.loadedIds()).isEmpty();
        assertThat(manager.failures().get("old")).startsWith("incompatible SDK:").contains("0.8");
    }

    @Test
    void malformedSdkVersionIsReportedAsRegistryError() throws Exception {
        descriptor("broken-sdk", "broken-sdk", "not-a-version");
        PluginManager manager = manager();

        manager.loadAll();

        assertThat(manager.declaredIds()).isEmpty();
        assertThat(manager.failures().get("broken-sdk"))
                .startsWith("invalid sdk-version 'not-a-version'");
    }

    @Test
    void dependencyOnIncompatiblePluginRejectsDependentToo() throws Exception {
        descriptor("old", "old", "0.8");
        descriptor("consumer", "consumer", "0.9", "old");
        PluginManager manager = manager();

        manager.loadAll();

        assertThat(manager.declaredIds()).isEmpty();
        assertThat(manager.failures()).containsKeys("old", "consumer");
        assertThat(manager.failures().get("consumer")).contains("missing dependency").contains("old");
    }

    @Test
    void dependencyOnDisabledPluginRejectsDependentToo() throws Exception {
        descriptor("disabled", "disabled", "0.9");
        descriptor("consumer", "consumer", "0.9", "disabled");
        PluginManager manager = new PluginManager(
                temp, d -> null, d -> { }, id -> !id.equals("disabled"));

        manager.loadAll();

        assertThat(manager.declaredIds()).isEmpty();
        assertThat(manager.failures()).containsKey("consumer").doesNotContainKey("disabled");
        assertThat(manager.failures().get("consumer")).contains("missing dependency").contains("disabled");
    }

    @Test
    void malformedManifestRemainsVisibleInRegistryWithoutBeingDeclared() throws Exception {
        Path broken = Files.createDirectories(temp.resolve("broken"));
        Files.writeString(broken.resolve("plugin.yml"), "id: [unterminated");
        PluginManager manager = manager();

        assertThat(manager.discover()).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo("broken");
            assertThat(d.directory()).isEqualTo(broken);
        });
        manager.loadAll();

        assertThat(manager.declaredIds()).isEmpty();
        assertThat(manager.failures().get("broken")).startsWith("bad plugin.yml:");
    }

    private PluginManager manager() {
        return new PluginManager(temp, d -> null, d -> { });
    }

    private void descriptor(String directory, String id, String sdk, String... dependencies) throws Exception {
        Path plugin = Files.createDirectories(temp.resolve(directory));
        Files.writeString(plugin.resolve("plugin.yml"), """
                id: %s
                version: 1.0
                sdk-version: "%s"
                module: %s
                depends-on: [%s]
                activation: onDemand
                """.formatted(id, sdk, id, String.join(", ", dependencies)));
    }
}
