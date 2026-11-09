package ru.mcs.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.plugin.Activation;
import ru.mcs.core.plugin.Contributions;
import ru.mcs.core.plugin.PluginDescriptor;

class PluginMessagesTest {

    @TempDir
    Path dir;

    private Path jar;

    @BeforeEach
    void setUp() throws IOException {
        jar = dir.resolve("plugin.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out)) {
            entry(jos, "p/messages.properties", "hello=Hello\nbye=Bye\ngreet=Hi {0}\n");
            entry(jos, "p/messages_ru.properties", "hello=Привет\ngreet=Здравствуйте, {0}\n");
        }
    }

    private static void entry(JarOutputStream jos, String name, String text) throws IOException {
        jos.putNextEntry(new JarEntry(name));
        jos.write(text.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }

    @Test
    void englishUsesRootFile() {
        PluginMessages m = PluginMessages.fromJars(List.of(jar), "p/messages", Locale.ENGLISH);
        assertThat(m.get("hello")).isEqualTo("Hello");
        assertThat(m.getLocale()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void russianOverridesAndFallsBackToEnglishForMissingKeys() {
        PluginMessages m = PluginMessages.fromJars(List.of(jar), "p/messages", Locale.of("ru"));
        assertThat(m.get("hello")).isEqualTo("Привет");
        assertThat(m.get("bye")).isEqualTo("Bye");
    }

    @Test
    void argumentsAreFormatted() {
        PluginMessages m = PluginMessages.fromJars(List.of(jar), "p/messages", Locale.of("ru"));
        assertThat(m.get("greet", "Аня")).isEqualTo("Здравствуйте, Аня");
    }

    @Test
    void unknownKeyReturnsTheKey() {
        PluginMessages m = PluginMessages.fromJars(List.of(jar), "p/messages", Locale.ENGLISH);
        assertThat(m.get("nope")).isEqualTo("nope");
    }

    @Test
    void createReadsJarsFromPluginDirectoryAndIgnoresOtherFiles() throws IOException {
        Files.writeString(dir.resolve("readme.txt"), "not a jar");
        PluginDescriptor d = new PluginDescriptor("p", "P", "1", "0.8", "m", List.of(), List.of(), dir,
                Activation.ON_DEMAND, Contributions.NONE, "p/messages");
        assertThat(PluginMessages.create(d, Locale.of("ru")).get("hello")).isEqualTo("Привет");
    }

    @Test
    void descriptorWithoutI18nGivesPassthroughKeys() {
        PluginDescriptor d = new PluginDescriptor("p", "P", "1", "0.8", "m", List.of(), List.of(), dir);
        assertThat(PluginMessages.create(d, Locale.ENGLISH).get("hello")).isEqualTo("hello");
    }

    @Test
    void missingBundleInJarGivesPassthroughKeys() {
        PluginMessages m = PluginMessages.fromJars(List.of(jar), "other/messages", Locale.ENGLISH);
        assertThat(m.get("hello")).isEqualTo("hello");
    }
}
