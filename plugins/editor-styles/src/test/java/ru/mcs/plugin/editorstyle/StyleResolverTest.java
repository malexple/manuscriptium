package ru.mcs.plugin.editorstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.CssDeclaration;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.CssLength;
import ru.mcs.spi.editor.style.CssOrigin;
import ru.mcs.spi.editor.style.CssRule;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleElement;
import ru.mcs.spi.editor.style.StyleSheet;
import ru.mcs.spi.editor.style.TextDecoration;

class StyleResolverTest {

    private final DefaultStyleResolver resolver = new DefaultStyleResolver();

    @Test
    void cascadeUsesImportantSpecificityOrderAndVariables() {
        StyleSheet sheet = sheet("""
                :root { --accent: #123456; --font: 18px; }
                p { color: #111111; font-size: 12px; }
                p { color: #222222; }
                p.note { color: var(--accent); padding: 1px 2px 3px 4px; }
                p { color: #000000 !important; font-size: var(--font); }
                """);

        ComputedStyle style = resolver.resolve(BlockKind.PARAGRAPH, List.of("note"),
                ElementState.tag("p"), sheet);

        assertThat(style.color()).isEqualTo(Color.BLACK);
        assertThat(style.fontSizePx()).isEqualTo(18);
        assertThat(style.padding().top()).isEqualTo(CssLength.px(1));
        assertThat(style.padding().right()).isEqualTo(CssLength.px(2));
        assertThat(style.padding().bottom()).isEqualTo(CssLength.px(3));
        assertThat(style.padding().left()).isEqualTo(CssLength.px(4));
        assertThat(style.variables()).containsEntry("--accent", "#123456");
    }

    @Test
    void selectorsSupportDescendantChildHoverAndNthChild() {
        StyleSheet sheet = sheet("""
                #write > blockquote.note { padding-left: 9px; }
                table tr:nth-child(even) { background-color: #abcdef; }
                a:hover { color: #ff0000; }
                a { color: #0000ff; }
                """);
        List<StyleElement> quoteAncestors = List.of(new StyleElement("div", "write", List.of(), 0));
        ComputedStyle quote = resolver.resolve(BlockKind.BLOCK_QUOTE, List.of("note"),
                new ElementState("blockquote", "", false, 0, quoteAncestors), sheet);
        assertThat(quote.padding().left()).isEqualTo(CssLength.px(9));

        List<StyleElement> rowAncestors = List.of(StyleElement.of("table"));
        ComputedStyle even = resolver.resolve(BlockKind.TABLE_ROW, List.of(),
                new ElementState("tr", "", false, 2, rowAncestors), sheet);
        ComputedStyle odd = resolver.resolve(BlockKind.TABLE_ROW, List.of(),
                new ElementState("tr", "", false, 1, rowAncestors), sheet);
        assertThat(even.backgroundColor()).isEqualTo(new Color(0xabcdef));
        assertThat(odd.backgroundColor().getAlpha()).isZero();

        ComputedStyle link = resolver.resolve(BlockKind.LINK, List.of(),
                ElementState.tag("a").withHover(true), sheet);
        assertThat(link.color()).isEqualTo(Color.RED);
    }

    @Test
    void variableFallbackAndNonfatalDiagnosticsWork() {
        StyleSheet sheet = sheet("""
                :root { --known: #010203; --cycle: var(--cycle); }
                p { color: var(--known); background-color: var(--missing, #040506); }
                code { color: var(--cycle, #070809); text-rendering: geometricPrecision; }
                @media (max-width: 600px) { p { color: red; } }
                """);

        ComputedStyle paragraph = resolver.resolve(BlockKind.PARAGRAPH, List.of(), ElementState.DEFAULT, sheet);
        ComputedStyle code = resolver.resolve(BlockKind.CODE, List.of(), ElementState.DEFAULT, sheet);

        assertThat(paragraph.color()).isEqualTo(new Color(0x010203));
        assertThat(paragraph.backgroundColor()).isEqualTo(new Color(0x040506));
        assertThat(code.color()).isEqualTo(new Color(0x070809));
        assertThat(sheet.diagnostics()).isNotEmpty().allMatch(d -> d.level() != CssDiagnostic.Level.ERROR);
    }

    @Test
    void userLayersOverrideThemeAndBaseUser() throws Exception {
        Path directory = Files.createTempDirectory("e4-cascade");
        Path theme = directory.resolve("custom.css");
        Path baseUser = directory.resolve("base.user.css");
        Path themeUser = directory.resolve("custom.user.css");
        Files.writeString(theme, "p { color: #111111; padding: 4px; }");
        Files.writeString(baseUser, "p { color: #222222; padding: 8px; }");
        Files.writeString(themeUser, "p { color: #333333; }");

        StyleSheet sheet = new TyporaCssLoader().load(theme, Optional.of(baseUser), Optional.of(themeUser));
        ComputedStyle style = resolver.resolve(BlockKind.PARAGRAPH, List.of(), ElementState.DEFAULT, sheet);

        assertThat(style.color()).isEqualTo(new Color(0x333333));
        assertThat(style.padding().top()).isEqualTo(CssLength.px(8));
        assertThat(sheet.rules()).anyMatch(r -> r.origin() == CssOrigin.THEME_USER);
    }

    private static StyleSheet sheet(String css) {
        CssParser.ParseResult parsed = new CssParser().parse(css, "test.css", null, CssOrigin.THEME, 0);
        return new StyleSheet(parsed.rules(), parsed.fontFaces(), parsed.diagnostics());
    }
}
