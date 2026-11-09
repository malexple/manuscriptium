package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginDescriptorParserTest {

    @Test
    void parsesFullDescriptor() {
        String yml = """
                id: ru.mcs.plugin.x
                name: X
                version: 1.2.3
                sdk-version: "0.1"
                module: ru.mcs.plugin.x
                depends-on: [ru.mcs.plugin.a]
                permissions: [network]
                """;
        PluginDescriptor d = PluginDescriptorParser.parse(new StringReader(yml), Path.of("/p"));
        assertThat(d.id()).isEqualTo("ru.mcs.plugin.x");
        assertThat(d.sdkVersion()).isEqualTo("0.1");
        assertThat(d.dependsOn()).containsExactly("ru.mcs.plugin.a");
        assertThat(d.permissions()).containsExactly("network");
    }

    @Test
    void nameDefaultsToId() {
        String yml = "id: a\nversion: 1\nsdk-version: '0.1'\nmodule: m\n";
        assertThat(PluginDescriptorParser.parse(new StringReader(yml), Path.of("/p")).name()).isEqualTo("a");
    }

    @Test
    void missingRequiredFieldFails() {
        assertThatThrownBy(() -> PluginDescriptorParser.parse(new StringReader("version: 1\n"), Path.of("/p")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }
}
