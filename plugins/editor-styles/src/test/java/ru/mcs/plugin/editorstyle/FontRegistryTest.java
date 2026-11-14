package ru.mcs.plugin.editorstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.FontFaceDefinition;
import ru.mcs.spi.editor.style.StyleSheet;

class FontRegistryTest {

    @Test
    void missingLocalFontProducesDiagnosticAndFallbackRemainsUsable() {
        FontRegistry registry = new FontRegistry();
        StyleSheet sheet = new StyleSheet(List.of(), List.of(
                new FontFaceDefinition("Missing Editor Font", "missing.ttf", 700, false)), List.of());
        registry.register(sheet, Path.of("build/font-test"));
        ComputedStyle style = ComputedStyle.defaults();

        Font font = registry.font(style, 23f);

        assertThat(font.getSize2D()).isEqualTo(23f);
        assertThat(registry.diagnostics()).singleElement()
                .satisfies(d -> assertThat(d.message()).contains("theme directory"));
    }
}
