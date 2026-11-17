package ru.mcs.markdown.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** JPMS и dependency-границы единого встроенного Markdown-модуля. */
class MarkdownModuleBoundaryTest {

    private static final Path CLASSES = Path.of("build", "classes", "java", "main");
    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final List<String> RETIRED_MODULES = List.of(
            "hello", "markdown-parser", "markdown-editor", "editor-styles", "math-renderer", "properties", "notes");

    @Test
    void productionClassesDoNotReferenceCoreOrRetiredPluginPackages() throws IOException {
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(CLASSES)) {
            classFiles = files.filter(path -> path.getFileName().toString().endsWith(".class")).toList();
        }
        assertThat(classFiles).as("скомпилированные классы markdown").isNotEmpty();
        List<String> forbidden = List.of("ru/mcs/core", "ru.mcs.core", "ru/mcs/plugin", "ru.mcs.plugin");
        List<String> offenders = new ArrayList<>();
        for (Path file : classFiles) {
            for (String constant : utf8Constants(file)) {
                String normalized = constant.toLowerCase(Locale.ROOT);
                if (forbidden.stream().anyMatch(normalized::contains)) {
                    offenders.add(CLASSES.relativize(file) + " -> " + constant);
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void moduleExportsOnlyApiAndHasNoPluginProvider() throws IOException {
        ModuleDescriptor descriptor;
        try (InputStream input = Files.newInputStream(CLASSES.resolve("module-info.class"))) {
            descriptor = ModuleDescriptor.read(input);
        }
        assertThat(descriptor.name()).isEqualTo("ru.mcs.markdown");
        assertThat(descriptor.exports()).extracting(export -> export.source())
                .containsExactly("ru.mcs.markdown.api");
        assertThat(descriptor.requires().stream().map(ModuleDescriptor.Requires::name))
                .contains("ru.mcs.spi.swing", "ru.mcs.editor")
                .doesNotContain("ru.mcs.core", "ru.mcs.plugin.mdeditor", "ru.mcs.plugin.mdparser");
        assertThat(descriptor.provides()).isEmpty();
        List<String> publicSignatures = new ArrayList<>();
        Class<?> api = MarkdownModuleMarker.apiClass();
        for (var method : api.getMethods()) {
            publicSignatures.add(method.toGenericString());
        }
        for (var constructor : api.getConstructors()) {
            publicSignatures.add(constructor.toGenericString());
        }
        for (var field : api.getFields()) {
            publicSignatures.add(field.toGenericString());
        }
        assertThat(publicSignatures).noneMatch(signature -> {
            String normalized = signature.toLowerCase(Locale.ROOT);
            return normalized.contains("flexmark") || normalized.contains("jlatexmath");
        });
    }

    /** Не инициализирует реализацию: проверяются только публичные JVM-сигнатуры API. */
    private static final class MarkdownModuleMarker {
        private static Class<?> apiClass() {
            try {
                return Class.forName("ru.mcs.markdown.api.MarkdownModule", false,
                        MarkdownModuleBoundaryTest.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    void coreHasNoProductionDependencyOnMarkdown() throws IOException {
        Path coreSources = Path.of("..", "core", "src", "main", "java");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(coreSources)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                String text = read(file);
                if (text.contains("ru.mcs.markdown") || text.contains("ru/mcs/markdown")) {
                    offenders.add(file.toString());
                }
            }
        }
        assertThat(offenders).isEmpty();
        assertThat(read(Path.of("..", "core", "build.gradle"))).doesNotContain(":markdown");
    }

    @Test
    void retiredPluginsAreGoneAndResourcesStayInMarkdown() throws IOException {
        for (String module : RETIRED_MODULES) {
            assertAbsentOrEmptyAfterPatch(Path.of("..", "plugins", module));
        }
        assertThat(Path.of("..", "settings.gradle")).content().contains("'markdown'").doesNotContain("plugins:");
        assertThat(Path.of("..", "app", "build.gradle")).content().contains("project(':markdown')");
        for (String theme : List.of("github", "newsprint", "night", "pixyll", "whitey", "vue",
                "lapis", "onedark", "notion")) {
            assertThat(Path.of("src", "main", "resources", "ru", "mcs", "markdown", "style", "themes",
                    theme + ".css")).isRegularFile();
        }
        assertThat(Path.of("src", "main", "resources", "ru", "mcs", "markdown", "messages",
                "messages_ru.properties")).isRegularFile();
        assertThat(Path.of("plugin.yml")).doesNotExist();
    }

    private static void assertAbsentOrEmptyAfterPatch(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        // Apply Patch удаляет файлы, но может оставить их пустые каталоги.
        assertThat(path).isDirectory();
        try (Stream<Path> entries = Files.walk(path)) {
            assertThat(entries.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> utf8Constants(Path file) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (input.readInt() != 0xCAFEBABE) {
                throw new IOException("Not a class file: " + file);
            }
            input.skipBytes(4);
            int count = input.readUnsignedShort();
            List<String> values = new ArrayList<>();
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> values.add(input.readUTF());
                    case 3, 4 -> input.skipBytes(4);
                    case 5, 6 -> {
                        input.skipBytes(8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> input.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipBytes(4);
                    case 15 -> input.skipBytes(3);
                    default -> throw new IOException("Unsupported constant pool tag " + tag + " in " + file);
                }
            }
            return values;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
