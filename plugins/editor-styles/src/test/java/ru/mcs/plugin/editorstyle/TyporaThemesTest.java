package ru.mcs.plugin.editorstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.CssEdges;
import ru.mcs.spi.editor.style.CssLength;
import ru.mcs.spi.editor.style.EditorThemeInfo;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleSheet;

class TyporaThemesTest {

    private final TyporaCssLoader loader = new TyporaCssLoader();
    private final ThemeCatalog catalog = new ThemeCatalog(loader, Path.of("build/test-editor-themes"));
    private final DefaultStyleResolver resolver = new DefaultStyleResolver();

    @Test
    void allSixBuiltInAndThreeAdditionalThemesLoadWithoutFatalDiagnostics() {
        assertThat(catalog.availableThemes()).extracting(EditorThemeInfo::id)
                .containsExactly("github", "newsprint", "night", "pixyll", "whitey", "vue",
                        "lapis", "onedark", "notion");
        for (EditorThemeInfo theme : catalog.availableThemes()) {
            ThemeCatalog.LoadedTheme loaded = catalog.load(theme.id());
            assertThat(loaded.sheet().rules()).as(theme.id()).isNotEmpty();
            assertThat(loaded.sheet().diagnostics()).as(theme.id()).isNotEmpty()
                    .allMatch(d -> d.level() != CssDiagnostic.Level.ERROR);
            ComputedStyle paragraph = resolver.resolve(BlockKind.PARAGRAPH, List.of(),
                    ElementState.DEFAULT, loaded.sheet());
            assertThat(paragraph.fontSizePx()).as(theme.id()).isEqualTo(16);
        }
    }

    @Test
    void githubComputesFixedValuesForRequiredBlocks() {
        StyleSheet sheet = catalog.load("github").sheet();

        ComputedStyle h1 = resolver.resolve(BlockKind.HEADING, List.of(), ElementState.tag("h1"), sheet);
        assertThat(h1.color()).isEqualTo(new Color(0x1f2328));
        assertThat(h1.fontSizePx()).isEqualTo(32);
        assertThat(h1.fontWeight()).isEqualTo(600);
        assertThat(h1.borders().bottom().width()).isEqualTo(CssLength.px(1));
        assertThat(h1.borders().bottom().color()).isEqualTo(new Color(0xd1d9e0));

        ComputedStyle quote = resolver.resolve(BlockKind.BLOCK_QUOTE, List.of(),
                ElementState.tag("blockquote"), sheet);
        assertThat(quote.color()).isEqualTo(new Color(0x59636e));
        assertThat(quote.backgroundColor()).isEqualTo(new Color(0xf6f8fa));
        assertThat(quote.borders().left().width()).isEqualTo(CssLength.px(4));
        assertThat(quote.borders().left().color()).isEqualTo(new Color(0x0969da));

        ComputedStyle pre = resolver.resolve(BlockKind.FENCED_CODE, List.of(), ElementState.tag("pre"), sheet);
        assertThat(pre.fontSizePx()).isEqualTo(14);
        assertThat(pre.padding()).isEqualTo(CssEdges.all(CssLength.px(16)));
        assertThat(pre.backgroundColor()).isEqualTo(new Color(0xf6f8fa));
        assertThat(pre.borderRadius()).isEqualTo(CssLength.px(6));

        ComputedStyle table = resolver.resolve(BlockKind.TABLE, List.of(), ElementState.tag("table"), sheet);
        assertThat(table.width()).isEqualTo(new CssLength(100, CssLength.Unit.PERCENT));
        assertThat(table.borders().top().width()).isEqualTo(CssLength.px(1));
    }
}
