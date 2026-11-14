package ru.mcs.plugin.editorstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.editor.style.CssLength;
import ru.mcs.spi.editor.style.CssOrigin;

class CssValuesTest {

    @Test
    void parsesRequiredColorFormatsAndUnits() {
        assertThat(CssValues.color("#abc")).isEqualTo(new Color(0xaabbcc));
        assertThat(CssValues.color("#11223380")).isEqualTo(new Color(17, 34, 51, 128));
        assertThat(CssValues.color("rgb(1, 2, 3)")).isEqualTo(new Color(1, 2, 3));
        assertThat(CssValues.color("rgba(1, 2, 3, 0.5)")).isEqualTo(new Color(1, 2, 3, 128));
        assertThat(CssValues.color("hsl(0, 100%, 50%)")).isEqualTo(Color.RED);
        assertThat(CssValues.color("rebeccapurple")).isEqualTo(new Color(102, 51, 153));

        assertThat(CssValues.length("12pt", false).toPx(16, 16)).isEqualTo(16);
        assertThat(CssValues.length("2em", false).toPx(20, 16)).isEqualTo(40);
        assertThat(CssValues.length("2rem", false).toPx(20, 16)).isEqualTo(32);
        assertThat(CssValues.length("50%", false).toPx(800, 16)).isEqualTo(400);
        assertThat(CssValues.length("1.5", true).unit()).isEqualTo(CssLength.Unit.NUMBER);
    }

    @Test
    void parsesLocalFontFaceAndRejectsNetworkFont() {
        CssParser parser = new CssParser();
        CssParser.ParseResult local = parser.parse("""
                @font-face {
                    font-family: 'Editor Custom';
                    src: url('editor.ttf');
                    font-weight: 600;
                    font-style: italic;
                }
                """, "local.css", null, CssOrigin.THEME, 0);
        assertThat(local.fontFaces()).singleElement().satisfies(font -> {
            assertThat(font.family()).isEqualTo("Editor Custom");
            assertThat(font.source()).isEqualTo("editor.ttf");
            assertThat(font.weight()).isEqualTo(600);
            assertThat(font.italic()).isTrue();
        });

        CssParser.ParseResult network = parser.parse("""
                @font-face { font-family: Remote; src: url(https://example.com/font.ttf); }
                """, "remote.css", null, CssOrigin.THEME, 0);
        assertThat(network.fontFaces()).isEmpty();
        assertThat(network.diagnostics()).isNotEmpty();
    }
}
