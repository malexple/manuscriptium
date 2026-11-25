package ru.mcs.markdown.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.view.TokenColors;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;
import ru.mcs.spi.swing.editor.style.TokenDefinition;
import ru.mcs.spi.swing.editor.style.TokenGroup;
import ru.mcs.spi.swing.editor.style.TokenSpan;

class MarkdownColorSchemeTest {

    @Test
    void groupCoversEveryTokenWithDefaultPaletteAndASampleThatShowsAllOfThem() {
        TokenGroup group = MarkdownColorScheme.group(key -> key);
        MarkdownTokenColors defaults = new MarkdownTokenColors();

        assertThat(group.tokens()).extracting(TokenDefinition::id).containsExactlyElementsOf(MarkdownTokens.ALL);
        for (TokenDefinition definition : group.tokens()) {
            assertThat(definition.rgb(false)).isEqualTo(defaults.color(definition.id(), false).getRGB() & 0xFFFFFF);
            assertThat(definition.rgb(true)).isEqualTo(defaults.color(definition.id(), true).getRGB() & 0xFFFFFF);
        }
        assertThat(group.preview().highlight(group.sample())).extracting(TokenSpan::token)
                .containsAll(MarkdownTokens.ALL);
    }

    @Test
    void colorsFollowServiceOverridesAndFallBackToDefaults() {
        FakeService service = new FakeService();
        TokenColors colors = MarkdownColorScheme.colors(service);

        assertThat(colors.color(MarkdownTokens.BOLD, false))
                .isEqualTo(new MarkdownTokenColors().color(MarkdownTokens.BOLD, false));

        service.overrides.put(ColorSchemeService.key(MarkdownTokens.BOLD, false), 0x010203);
        assertThat(colors.color(MarkdownTokens.BOLD, false)).isEqualTo(new Color(0x010203));
        assertThat(colors.color(MarkdownTokens.BOLD, true))
                .isEqualTo(new MarkdownTokenColors().color(MarkdownTokens.BOLD, true));
    }

    @Test
    void everyTokenHasAnEnglishAndRussianName() throws IOException {
        for (String resource : List.of("messages.properties", "messages_ru.properties")) {
            Properties properties = new Properties();
            try (var stream = MarkdownColorSchemeTest.class.getResourceAsStream(
                    "/ru/mcs/markdown/messages/" + resource)) {
                assertThat(stream).as(resource).isNotNull();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            for (String token : MarkdownTokens.ALL) {
                assertThat(properties.getProperty("token." + token)).as(resource + " " + token).isNotBlank();
            }
        }
    }

    private static final class FakeService implements ColorSchemeService {

        private final Map<String, Integer> overrides = new HashMap<>();

        @Override
        public void registerGroup(TokenGroup group) {
        }

        @Override
        public void unregisterGroup(String id) {
        }

        @Override
        public List<TokenGroup> groups() {
            return new ArrayList<>();
        }

        @Override
        public Color color(String token, boolean dark) {
            Integer value = overrides.get(ColorSchemeService.key(token, dark));
            return value == null ? null : new Color(value);
        }

        @Override
        public Map<String, Integer> overrides() {
            return overrides;
        }

        @Override
        public void setOverrides(Map<String, Integer> next) {
        }

        @Override
        public void addChangeListener(Runnable listener) {
        }

        @Override
        public void removeChangeListener(Runnable listener) {
        }
    }
}
