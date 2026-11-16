package ru.mcs.editor.architecture;

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

/** Архитектурная граница языконезависимого редактора в production classfiles и JPMS. */
class EditorPurityTest {

    private static final Path CLASSES = Path.of("build", "classes", "java", "main");
    private static final List<String> FORBIDDEN = List.of(
            "markdown", "obsidian", "frontmatter", "flexmark", "jlatexmath", "ru/mcs/core", "ru.mcs.core");

    @Test
    void productionClassesDoNotReferenceLanguagesOrCore() throws IOException {
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(CLASSES)) {
            classFiles = files.filter(path -> path.getFileName().toString().endsWith(".class")).toList();
        }
        assertThat(classFiles).as("скомпилированные классы editor").isNotEmpty();
        List<String> offenders = new ArrayList<>();
        for (Path file : classFiles) {
            for (String constant : utf8Constants(file)) {
                String normalized = constant.toLowerCase(Locale.ROOT);
                if (FORBIDDEN.stream().anyMatch(normalized::contains)) {
                    offenders.add(CLASSES.relativize(file) + " -> " + constant);
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void moduleRequiresOnlySpiSwingAndPublishesTextProvider() throws IOException {
        ModuleDescriptor descriptor;
        try (InputStream input = Files.newInputStream(CLASSES.resolve("module-info.class"))) {
            descriptor = ModuleDescriptor.read(input);
        }
        assertThat(descriptor.name()).isEqualTo("ru.mcs.editor");
        assertThat(descriptor.requires()).anySatisfy(requires -> {
            assertThat(requires.name()).isEqualTo("ru.mcs.spi.swing");
            assertThat(requires.modifiers()).contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
        });
        assertThat(descriptor.requires().stream().map(ModuleDescriptor.Requires::name))
                .doesNotContain("ru.mcs.core", "ru.mcs.plugin.mdeditor");
        assertThat(descriptor.provides()).anySatisfy(provides -> {
            assertThat(provides.service()).isEqualTo("ru.mcs.spi.swing.editor.FileEditorProvider");
            assertThat(provides.providers()).contains("ru.mcs.editor.text.TextFileEditorProvider");
        });
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
