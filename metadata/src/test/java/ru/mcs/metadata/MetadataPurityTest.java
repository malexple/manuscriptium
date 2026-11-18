package ru.mcs.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MetadataPurityTest {

    @Test
    void productionHasNoUiOrApplicationDependencies() throws IOException {
        List<String> forbidden = List.of("java.awt", "javax.swing", "android.", "androidx.",
                "compose", "ru.mcs.core", "ru.mcs.editor", "ru.mcs.markdown");
        try (Stream<Path> paths = Files.walk(Path.of("src", "main"))) {
            List<String> offenders = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            String text = Files.readString(path);
                            return forbidden.stream().filter(text::contains).map(term -> path + " -> " + term);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }).toList();
            assertThat(offenders).isEmpty();
        }
        assertThat(Path.of("build.gradle")).content().doesNotContain("project(':core')", "project(':editor')",
                "project(':markdown')", "flatlaf", "compose", "android");
    }
}
