package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginInstallerTest {

    @TempDir
    Path temp;

    @Test
    void installsImmediatelyAndAppliesReplacementOnNextStartup() throws Exception {
        Path source = directoryPlugin("1.0");
        Path plugins = temp.resolve("plugins");
        PluginInstaller installer = new PluginInstaller(plugins);

        PluginInstaller.Inspection first = installer.inspect(source);
        assertThat(first.descriptor().permissions()).containsExactly("filesystem", "network");
        assertThat(installer.install(first).version()).isEqualTo("1.0");
        assertThat(plugins.resolve("demo/plugin.yml")).isRegularFile();
        assertThat(plugins.resolve("demo/demo.jar")).isRegularFile();

        Files.writeString(source.resolve("plugin.yml"), manifest("2.0"));
        PluginInstaller.Inspection update = installer.inspect(source);
        assertThat(installer.isInstalled("demo")).isTrue();
        assertThat(installer.install(update).version()).isEqualTo("2.0");
        assertThat(PluginDescriptorParser.parse(plugins.resolve("demo/plugin.yml")).version()).isEqualTo("1.0");
        assertThat(PluginDescriptorParser.parse(plugins.resolve(".demo.pending/plugin.yml")).version())
                .isEqualTo("2.0");

        installer.applyPending();

        assertThat(PluginDescriptorParser.parse(plugins.resolve("demo/plugin.yml")).version()).isEqualTo("2.0");
        assertThat(plugins.resolve(".demo.pending")).doesNotExist();
        try (var paths = Files.list(plugins)) {
            assertThat(paths.map(p -> p.getFileName().toString()).toList()).containsExactly("demo");
        }
    }

    @Test
    void replacesExistingDirectoryFoundByManifestIdRatherThanDirectoryName() throws Exception {
        Path plugins = temp.resolve("plugins");
        Path existing = Files.createDirectories(plugins.resolve("hello"));
        Files.writeString(existing.resolve("plugin.yml"), manifest("1.0"));
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(existing.resolve("old.jar")))) {
            // Пустой, но валидный JAR: на Windows открытый JarFile заблокирует перемещение каталога.
        }
        Path source = directoryPlugin("2.0");
        PluginInstaller installer = new PluginInstaller(plugins);
        PluginInstaller.Inspection inspection = installer.inspect(source);

        assertThat(installer.isInstalled("demo")).isTrue();
        try (JarFile ignored = new JarFile(existing.resolve("old.jar").toFile())) {
            installer.install(inspection);

            assertThat(PluginDescriptorParser.parse(existing.resolve("plugin.yml")).version()).isEqualTo("1.0");
            assertThat(existing.resolve("old.jar")).isRegularFile();
            assertThat(plugins.resolve(".demo.pending/plugin.yml")).isRegularFile();
        }
        installer.applyPending();

        assertThat(plugins.resolve("hello/plugin.yml")).isRegularFile();
        assertThat(plugins.resolve("hello/demo.jar")).isRegularFile();
        assertThat(plugins.resolve("hello/old.jar")).doesNotExist();
        assertThat(plugins.resolve("demo")).doesNotExist();
        try (var paths = Files.list(plugins)) {
            assertThat(paths.map(p -> p.getFileName().toString()).toList()).containsExactly("hello");
        }
    }

    @Test
    void installsZipWithSingleTopLevelDirectory() throws Exception {
        Path source = temp.resolve("demo.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            entry(zip, "demo/plugin.yml", manifest("1.0"));
            entry(zip, "demo/lib/demo.jar", "jar");
        }
        PluginInstaller installer = new PluginInstaller(temp.resolve("plugins"));

        PluginInstaller.Inspection inspection = installer.inspect(source);
        assertThat(inspection.kind()).isEqualTo(PluginInstaller.SourceKind.ZIP);
        installer.install(inspection);

        assertThat(temp.resolve("plugins/demo/plugin.yml")).isRegularFile();
        assertThat(temp.resolve("plugins/demo/lib/demo.jar")).isRegularFile();
    }

    @Test
    void jarWithManifestKeepsJarAndExtractsOnlyPluginYml() throws Exception {
        Path source = temp.resolve("demo.jar");
        try (ZipOutputStream jar = new ZipOutputStream(Files.newOutputStream(source))) {
            entry(jar, "plugin.yml", manifest("1.0"));
            entry(jar, "module-info.class", "bytecode");
        }
        PluginInstaller installer = new PluginInstaller(temp.resolve("plugins"));

        installer.install(installer.inspect(source));

        assertThat(temp.resolve("plugins/demo/demo.jar")).isRegularFile();
        assertThat(temp.resolve("plugins/demo/plugin.yml")).isRegularFile();
        assertThat(temp.resolve("plugins/demo/module-info.class")).doesNotExist();
    }

    @Test
    void rejectsZipSlipWithoutCreatingTargetOrOutsideFiles() throws Exception {
        Path source = temp.resolve("malicious.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            entry(zip, "demo/plugin.yml", manifest("1.0"));
            entry(zip, "demo/demo.jar", "jar");
            entry(zip, "demo/../../escape.txt", "bad");
        }
        PluginInstaller installer = new PluginInstaller(temp.resolve("plugins"));
        PluginInstaller.Inspection inspection = installer.inspect(source);

        assertThatThrownBy(() -> installer.install(inspection)).isInstanceOf(IOException.class)
                .hasMessageContaining("Unsafe path");
        assertThat(temp.resolve("plugins/demo")).doesNotExist();
        assertThat(temp.resolve("plugins/escape.txt")).doesNotExist();
    }

    @Test
    void rejectsUnsafePluginIdBeforeWritingFiles() throws Exception {
        Path source = temp.resolve("unsafe");
        Files.createDirectories(source);
        Files.writeString(source.resolve("plugin.yml"), manifest("1.0").replace("id: demo", "id: ../escape"));
        Files.writeString(source.resolve("demo.jar"), "jar");
        PluginInstaller installer = new PluginInstaller(temp.resolve("plugins"));

        assertThatThrownBy(() -> installer.inspect(source)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe plugin id");
        assertThat(temp.resolve("plugins")).doesNotExist();
    }

    private Path directoryPlugin(String version) throws IOException {
        Path source = Files.createDirectories(temp.resolve("source"));
        Files.writeString(source.resolve("plugin.yml"), manifest(version));
        Files.writeString(source.resolve("demo.jar"), "jar");
        return source;
    }

    private static String manifest(String version) {
        return """
                id: demo
                name: Demo plugin
                version: %s
                sdk-version: "0.9"
                module: demo
                permissions: [filesystem, network]
                """.formatted(version);
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
