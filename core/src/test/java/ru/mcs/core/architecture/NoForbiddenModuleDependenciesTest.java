package ru.mcs.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Проверяет направление зависимостей одновременно в JPMS и Gradle, включая отсутствие markdown → core. */
class NoForbiddenModuleDependenciesTest {

    private static final Map<String, List<String>> FORBIDDEN = Map.of(
            "spi", List.of("spi-swing", "editor", "markdown", "core", "app"),
            "spi-swing", List.of("editor", "markdown", "core", "app"),
            "editor", List.of("markdown", "core", "app"),
            "core", List.of("markdown", "app"),
            "markdown", List.of("core", "app"));

    @Test
    void moduleDescriptorsAndGradleDependenciesPointOnlyDownward() throws IOException {
        List<String> offenders = new ArrayList<>();
        FORBIDDEN.forEach((source, targets) -> targets.forEach(target -> {
            Path root = moduleRoot(source);
            String descriptor = read(root.resolve("src/main/java/module-info.java"));
            String gradle = read(root.resolve("build.gradle"));
            Pattern jpms = Pattern.compile("(?m)^\\s*requires\\s+(?:(?:transitive|static)\\s+)*ru\\.mcs\\."
                    + Pattern.quote(target) + "\\s*;");
            Pattern project = Pattern.compile("project\\s*\\(\\s*['\"]:" + Pattern.quote(target) + "['\"]\\s*\\)");
            if (jpms.matcher(descriptor).find()) {
                offenders.add(source + " module-info -> " + target);
            }
            if (project.matcher(gradle).find()) {
                offenders.add(source + " build.gradle -> " + target);
            }
        }));
        assertThat(offenders).isEmpty();
    }

    private static Path moduleRoot(String module) {
        return module.equals("core") ? Path.of("").toAbsolutePath().normalize()
                : Path.of("..", module).toAbsolutePath().normalize();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read " + path, error);
        }
    }
}
