package ru.mcs.core.icon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.plugin.PluginDescriptor;

class PluginIconsTest {

    @TempDir
    Path directory;

    @Test
    void loadsSvgFromPluginJarWithoutModuleLayer() throws Exception {
        jar("hello.jar", "icons/message.svg", """
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                  <path d="M4 4 L20 20" stroke="currentColor" stroke-width="2"/>
                </svg>
                """);
        PluginDescriptor descriptor = descriptor();

        FlatSVGIcon icon = PluginIcons.load(descriptor, "/icons/message.svg");

        assertThat(icon.hasFound()).isTrue();
        assertThat(icon.getIconWidth()).isEqualTo(16);
        assertThat(icon.getColorFilter()).isNotNull();
    }

    @Test
    void reportsMissingPluginResource() throws Exception {
        jar("hello.jar", "plugin.txt", "not an icon");
        assertThatThrownBy(() -> PluginIcons.load(descriptor(), "/icons/missing.svg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining("ru.mcs.plugin.hello");
    }

    @Test
    void rejectsParentTraversal() {
        assertThatThrownBy(() -> PluginIcons.load(descriptor(), "/icons/../secret.svg"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid");
    }

    private PluginDescriptor descriptor() {
        return new PluginDescriptor("ru.mcs.plugin.hello", "Hello", "1", "0.9", "hello",
                List.of(), List.of(), directory);
    }

    private void jar(String name, String entryName, String content) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(directory.resolve(name)))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
