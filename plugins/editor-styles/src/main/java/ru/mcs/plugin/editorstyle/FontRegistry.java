package ru.mcs.plugin.editorstyle;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.mcs.spi.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.FontFaceDefinition;
import ru.mcs.spi.editor.style.StyleSheet;

/** Регистрирует локальные TTF/OTF и выбирает шрифт без обращения к CSS-парсеру из E5. */
final class FontRegistry {

    private record Key(String family, int weight, boolean italic) {
    }

    private volatile Map<Key, Font> fonts = Map.of();
    private volatile List<CssDiagnostic> diagnostics = List.of();

    void register(StyleSheet sheet, Path themeDirectory) {
        Map<Key, Font> loaded = new HashMap<>();
        List<CssDiagnostic> problems = new ArrayList<>();
        for (FontFaceDefinition definition : sheet.fontFaces()) {
            if (themeDirectory == null) {
                problems.add(CssDiagnostic.warning("<font-face>", 0,
                        "Built-in resources cannot reference external fonts: " + definition.family()));
                continue;
            }
            Path root = themeDirectory.toAbsolutePath().normalize();
            Path file = root.resolve(definition.source()).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                problems.add(CssDiagnostic.warning(file.toString(), 0,
                        "Font must be a regular local file inside the theme directory"));
                continue;
            }
            int format = definition.source().toLowerCase(Locale.ROOT).endsWith(".otf")
                    ? Font.TYPE1_FONT : Font.TRUETYPE_FONT;
            // Font.createFont официально поддерживает TTF; OTF с TrueType outlines также работает.
            if (format == Font.TYPE1_FONT) {
                format = Font.TRUETYPE_FONT;
            }
            try (InputStream input = Files.newInputStream(file)) {
                Font font = Font.createFont(format, input);
                loaded.put(new Key(definition.family().toLowerCase(Locale.ROOT),
                        definition.weight(), definition.italic()), font);
            } catch (Exception e) {
                problems.add(CssDiagnostic.warning(file.toString(), 0,
                        "Cannot load @font-face: " + e.getMessage()));
            }
        }
        fonts = Map.copyOf(loaded);
        diagnostics = List.copyOf(problems);
    }

    Font font(ComputedStyle style, float sizePx) {
        int awtStyle = (style.fontWeight() >= 600 ? Font.BOLD : Font.PLAIN)
                | (style.italic() ? Font.ITALIC : 0);
        for (String family : style.fontFamilies()) {
            Font custom = fonts.get(new Key(family.toLowerCase(Locale.ROOT), style.fontWeight(), style.italic()));
            if (custom != null) {
                return custom.deriveFont(awtStyle, sizePx);
            }
        }
        String family = style.fontFamilies().isEmpty() ? Font.SANS_SERIF : style.fontFamilies().getFirst();
        return new Font(family, awtStyle, Math.max(1, Math.round(sizePx))).deriveFont(sizePx);
    }

    List<CssDiagnostic> diagnostics() {
        return diagnostics;
    }
}
