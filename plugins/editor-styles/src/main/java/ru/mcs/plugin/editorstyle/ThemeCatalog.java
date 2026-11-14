package ru.mcs.plugin.editorstyle;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import ru.mcs.spi.editor.style.CssDeclaration;
import ru.mcs.spi.editor.style.CssRule;
import ru.mcs.spi.editor.style.EditorThemeInfo;
import ru.mcs.spi.editor.style.StyleSheet;

/** Каталог шести встроенных и трёх дополнительных тем плюс локальные CSS-файлы. */
final class ThemeCatalog {

    private record BuiltIn(String id, String name, boolean dark) {
    }

    record LoadedTheme(EditorThemeInfo info, StyleSheet sheet, Path themeDirectory) {
    }

    private static final List<BuiltIn> BUILT_INS = List.of(
            new BuiltIn("github", "GitHub", false),
            new BuiltIn("newsprint", "Newsprint", false),
            new BuiltIn("night", "Night", true),
            new BuiltIn("pixyll", "Pixyll", false),
            new BuiltIn("whitey", "Whitey", false),
            new BuiltIn("vue", "Vue", false),
            new BuiltIn("lapis", "Lapis", false),
            new BuiltIn("onedark", "OneDark", true),
            new BuiltIn("notion", "Notion", false));

    private final TyporaCssLoader loader;
    private final Path directory;

    ThemeCatalog(TyporaCssLoader loader, Path directory) {
        this.loader = loader;
        this.directory = directory;
    }

    Path directory() {
        return directory;
    }

    String defaultTheme(boolean dark) {
        return dark ? "night" : "github";
    }

    List<EditorThemeInfo> availableThemes() {
        Map<String, EditorThemeInfo> result = new LinkedHashMap<>();
        for (BuiltIn theme : BUILT_INS) {
            result.put(theme.id(), new EditorThemeInfo(theme.id(), theme.name(), theme.dark(), true));
        }
        for (Path file : externalFiles()) {
            String id = id(file);
            result.putIfAbsent(id, new EditorThemeInfo(id, displayName(id), false, false));
        }
        return List.copyOf(result.values());
    }

    LoadedTheme load(String id) {
        String normalized = validateId(id);
        Path external = directory == null ? null : directory.resolve(normalized + ".css");
        if (external != null && Files.isRegularFile(external)) {
            StyleSheet sheet = loader.load(external,
                    Optional.of(directory.resolve("base.user.css")),
                    Optional.of(directory.resolve(normalized + ".user.css")));
            return new LoadedTheme(new EditorThemeInfo(normalized, displayName(normalized),
                    isDark(sheet), false), sheet, directory);
        }
        BuiltIn builtIn = BUILT_INS.stream().filter(t -> t.id().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown editor theme: " + id));
        return new LoadedTheme(new EditorThemeInfo(builtIn.id(), builtIn.name(), builtIn.dark(), true),
                loader.loadBuiltIn(builtIn.id(), directory), null);
    }

    private List<Path> externalFiles() {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".css"))
                    .filter(path -> !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".user.css"))
                    .filter(path -> !"base.css".equalsIgnoreCase(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean isDark(StyleSheet sheet) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (CssRule rule : sheet.rules()) {
            if (rule.selectors().stream().anyMatch(":root"::equals)) {
                for (CssDeclaration declaration : rule.declarations()) {
                    if (declaration.name().startsWith("--")) {
                        variables.put(declaration.name(), declaration.value());
                    }
                }
            }
        }
        String background = variables.get("--bg-color");
        if (background == null) {
            return false;
        }
        try {
            Color color = CssValues.color(VariableResolver.resolve(background, variables));
            double luminance = (0.2126 * color.getRed() + 0.7152 * color.getGreen()
                    + 0.0722 * color.getBlue()) / 255.0;
            return luminance < 0.45;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String id(Path file) {
        String name = file.getFileName().toString();
        return validateId(name.substring(0, name.length() - 4));
    }

    private static String validateId(String id) {
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*") || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid editor theme id: " + id);
        }
        return normalized;
    }

    private static String displayName(String id) {
        if (id.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
