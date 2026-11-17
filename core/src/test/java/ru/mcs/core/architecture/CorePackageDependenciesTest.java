package ru.mcs.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Фиксирует направления, необходимые для последующего выделения модулей без package-циклов. */
class CorePackageDependenciesTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "ru", "mcs", "core");

    @Test
    void editorDoesNotDependBackOnProject() {
        assertNoReferences("editor", "project");
    }

    @Test
    void settingsThemeI18nAndPlatformHaveOneWayDependencies() {
        Map<String, List<String>> forbidden = Map.of(
                "theme", List.of("settings"),
                "i18n", List.of("plugin"),
                "platform", List.of("action", "settings", "shell", "project"),
                "fs", List.of("editor", "project"));
        forbidden.forEach((source, targets) -> targets.forEach(target -> assertNoReferences(source, target)));
    }

    @Test
    void productionDoesNotRetainOldProjectIdentifiers() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            List<String> offenders = files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> {
                        String text = read(path);
                        return text.contains("Vault") || text.contains("VAULT")
                                || text.contains("ru.mcs.core.vault");
                    })
                    .map(SOURCES::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
            assertThat(offenders).as("старые идентификаторы; legacy state id core.vault разрешён").isEmpty();
        }
    }

    private static void assertNoReferences(String sourcePackage, String forbiddenPackage) {
        String prefix = "ru.mcs.core." + forbiddenPackage + ".";
        assertThat(references(sourcePackage, prefix))
                .as("%s must not depend on %s", sourcePackage, forbiddenPackage)
                .isEmpty();
    }

    private static List<String> references(String sourcePackage, String prefix) {
        Path directory = SOURCES.resolve(sourcePackage);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> read(path).contains(prefix))
                    .map(SOURCES::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
