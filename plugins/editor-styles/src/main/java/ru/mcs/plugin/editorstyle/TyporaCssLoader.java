package ru.mcs.plugin.editorstyle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.CssOrigin;
import ru.mcs.spi.editor.style.CssRule;
import ru.mcs.spi.editor.style.FontFaceDefinition;
import ru.mcs.spi.editor.style.StyleSheet;

/** Собирает слои defaults → theme → base.user.css → {theme}.user.css без фатальных ошибок CSS. */
final class TyporaCssLoader {

    private final CssParser parser = new CssParser();
    private volatile List<CssDiagnostic> diagnostics = List.of();

    StyleSheet load(Path themeCss, Optional<Path> userCss) {
        return load(themeCss, Optional.empty(), userCss);
    }

    StyleSheet load(Path themeCss, Optional<Path> baseUserCss, Optional<Path> themeUserCss) {
        Layers layers = new Layers();
        appendResource(layers, "base.css", CssOrigin.DEFAULT);
        appendPath(layers, themeCss, CssOrigin.THEME);
        baseUserCss.ifPresent(path -> appendPath(layers, path, CssOrigin.BASE_USER));
        themeUserCss.ifPresent(path -> appendPath(layers, path, CssOrigin.THEME_USER));
        return finish(layers);
    }

    StyleSheet loadBuiltIn(String id, Path userDirectory) {
        Layers layers = new Layers();
        appendResource(layers, "base.css", CssOrigin.DEFAULT);
        appendResource(layers, id + ".css", CssOrigin.THEME);
        if (userDirectory != null) {
            appendPath(layers, userDirectory.resolve("base.user.css"), CssOrigin.BASE_USER);
            appendPath(layers, userDirectory.resolve(id + ".user.css"), CssOrigin.THEME_USER);
        }
        return finish(layers);
    }

    List<CssDiagnostic> diagnostics() {
        return diagnostics;
    }

    private StyleSheet finish(Layers layers) {
        diagnostics = List.copyOf(layers.diagnostics);
        return new StyleSheet(layers.rules, layers.fonts, layers.diagnostics);
    }

    private void appendResource(Layers layers, String name, CssOrigin origin) {
        String resource = "/ru/mcs/plugin/editorstyle/themes/" + name;
        try (InputStream input = TyporaCssLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing resource " + resource);
            }
            append(layers, new String(input.readAllBytes(), StandardCharsets.UTF_8),
                    "builtin:" + name, origin);
        } catch (IOException | RuntimeException e) {
            layers.diagnostics.add(new CssDiagnostic("builtin:" + name, 0,
                    CssDiagnostic.Level.ERROR, "Cannot load built-in editor theme: " + e.getMessage()));
        }
    }

    private void appendPath(Layers layers, Path path, CssOrigin origin) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            append(layers, Files.readString(path, StandardCharsets.UTF_8), path.toString(), origin);
        } catch (IOException | RuntimeException e) {
            layers.diagnostics.add(new CssDiagnostic(path.toString(), 0,
                    CssDiagnostic.Level.ERROR, "Cannot read editor theme: " + e.getMessage()));
        }
    }

    private void append(Layers layers, String css, String source, CssOrigin origin) {
        CssParser.ParseResult result = parser.parse(css, source, null, origin, layers.nextOrder);
        layers.rules.addAll(result.rules());
        layers.fonts.addAll(result.fontFaces());
        layers.diagnostics.addAll(result.diagnostics());
        layers.nextOrder = result.nextOrder();
    }

    private static final class Layers {
        private final List<CssRule> rules = new ArrayList<>();
        private final List<FontFaceDefinition> fonts = new ArrayList<>();
        private final List<CssDiagnostic> diagnostics = new ArrayList<>();
        private int nextOrder;
    }
}
