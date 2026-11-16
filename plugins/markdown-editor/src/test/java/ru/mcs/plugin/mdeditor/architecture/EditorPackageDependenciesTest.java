package ru.mcs.plugin.mdeditor.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Направления model/draw/layout/render/view, при которых универсальный editor можно отделить от Markdown. */
class EditorPackageDependenciesTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "ru", "mcs", "plugin", "mdeditor");
    private static final String INTERNAL = "ru.mcs.plugin.mdeditor.";

    @Test
    void keymapAndModesDoNotDependOnConcreteView() {
        assertNoReferences("action", "view");
        assertNoReferences("mode", "view");
    }

    @Test
    void layoutAndWidgetsDoNotDependBackOnRendering() {
        assertNoReferences("layout", "render");
        assertNoReferences("layout", "view");
        assertNoReferences("widget", "layout");
        assertNoReferences("widget", "view");
    }

    @Test
    void referencesAreIndependentFromHigherLevelPackages() {
        List.of("action", "layout", "mode", "render", "view", "widget")
                .forEach(target -> assertNoReferences("reference", target));
    }

    private static void assertNoReferences(String sourcePackage, String forbiddenPackage) {
        String prefix = INTERNAL + forbiddenPackage + ".";
        assertThat(references(sourcePackage, prefix))
                .as("%s must not depend on %s", sourcePackage, forbiddenPackage)
                .isEmpty();
    }

    private static List<String> references(String sourcePackage, String prefix) {
        Path directory = SOURCES.resolve(sourcePackage);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Missing source package: " + directory.toAbsolutePath());
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
