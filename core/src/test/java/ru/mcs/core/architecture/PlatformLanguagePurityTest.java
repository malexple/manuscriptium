package ru.mcs.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Запрещает протекание конкретного языка в production-код и ресурсы платформы. */
class PlatformLanguagePurityTest {

    private static final List<String> PLATFORM_MODULES = List.of("spi", "spi-swing", "editor", "core");
    private static final List<String> FORBIDDEN = List.of("markdown", "obsidian", "frontmatter", "front_matter");

    @Test
    void platformProductionSourcesAndResourcesAreLanguageNeutral() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : PLATFORM_MODULES) {
            Path production = moduleRoot(module).resolve("src/main");
            try (Stream<Path> paths = Files.walk(production)) {
                paths.filter(Files::isRegularFile)
                        .filter(PlatformLanguagePurityTest::isTextSource)
                        .forEach(path -> inspect(module, production, path, offenders));
            }
        }
        assertThat(offenders).isEmpty();
    }

    private static void inspect(String module, Path production, Path file, List<String> offenders) {
        String text = read(file).toLowerCase(Locale.ROOT);
        FORBIDDEN.stream().filter(text::contains).findFirst().ifPresent(term ->
                offenders.add(module + ":" + production.relativize(file) + " -> " + term));
    }

    private static boolean isTextSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".properties");
    }

    private static Path moduleRoot(String module) {
        return module.equals("core") ? Path.of("").toAbsolutePath().normalize()
                : Path.of("..", module).toAbsolutePath().normalize();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
