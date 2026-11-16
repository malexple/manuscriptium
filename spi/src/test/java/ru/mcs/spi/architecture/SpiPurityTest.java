package ru.mcs.spi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Гарантирует, что базовый SDK остаётся независимым от desktop UI даже в скомпилированных сигнатурах. */
class SpiPurityTest {

    private static final Path CLASSES = Path.of("build", "classes", "java", "main");

    @Test
    void classfilesDoNotReferenceAwtSwingOrJavaDesktop() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(CLASSES)) {
            classFiles = files.filter(path -> path.getFileName().toString().endsWith(".class")).toList();
        }
        assertThat(classFiles).as("скомпилированные классы spi").isNotEmpty();
        for (Path file : classFiles) {
            for (String constant : utf8Constants(file)) {
                if (constant.startsWith("java/awt/") || constant.startsWith("javax/swing/")
                        || constant.equals("java.desktop")) {
                    offenders.add(CLASSES.relativize(file) + " -> " + constant);
                }
            }
        }
        assertThat(offenders).isEmpty();
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
