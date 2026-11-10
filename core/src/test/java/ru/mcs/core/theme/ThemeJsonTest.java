package ru.mcs.core.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.io.StringReader;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;

class ThemeJsonTest {

    @Test
    void parsesThemeAndConvertsJsonTokensToUiValues() {
        ThemeDefinition theme = ThemeJson.parse(new StringReader("""
                {
                  "id": "custom",
                  "name": "My theme",
                  "dark": true,
                  "tokens": {
                    "Label.foreground": "#11223344",
                    "Button.arc": 12,
                    "Button.hoverEnabled": true,
                    "Component.author": "Me"
                  }
                }
                """), "fallback", null);

        assertThat(theme.id()).isEqualTo("custom");
        assertThat(theme.name()).isEqualTo("My theme");
        assertThat(theme.dark()).isTrue();
        assertThat(theme.tokens().get("Label.foreground")).isInstanceOf(ColorUIResource.class);
        assertThat((Color) theme.tokens().get("Label.foreground"))
                .isEqualTo(new Color(0x11, 0x22, 0x33, 0x44));
        assertThat(theme.tokens().get("Button.arc")).isEqualTo(12);
        assertThat(theme.tokens().get("Button.hoverEnabled")).isEqualTo(true);
    }

    @Test
    void fileNameIsUsedAsFallbackIdAndName() {
        ThemeDefinition theme = ThemeJson.parse(new StringReader("{\"dark\":false}"), "custom", null);
        assertThat(theme.id()).isEqualTo("custom");
        assertThat(theme.name()).isEqualTo("custom");
        assertThat(theme.tokens()).isEmpty();
    }

    @Test
    void rejectsReservedIdsMissingBaseAndInvalidTokens() {
        assertThatThrownBy(() -> parse("{\"id\":\"system\",\"dark\":false}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> parse("{\"id\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dark");
        assertThatThrownBy(() -> parse("{\"dark\":false,\"tokens\":{\"Label.foreground\":\"#xyz\"}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Color");
        assertThatThrownBy(() -> parse("{\"dark\":false,\"tokens\":{\"Panel\":{}}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token");
    }

    private static ThemeDefinition parse(String json) {
        return ThemeJson.parse(new StringReader(json), "x", null);
    }
}
