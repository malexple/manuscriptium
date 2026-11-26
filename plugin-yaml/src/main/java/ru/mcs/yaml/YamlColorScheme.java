package ru.mcs.yaml;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import ru.mcs.editor.view.HighlightSpan;
import ru.mcs.editor.view.TokenColors;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;
import ru.mcs.spi.swing.editor.style.TokenDefinition;
import ru.mcs.spi.swing.editor.style.TokenGroup;
import ru.mcs.spi.swing.editor.style.TokenSpan;

/** Подключает токены YAML к странице «Цветовая схема» и к рисованию редактора. */
final class YamlColorScheme {

    static final String GROUP_ID = "yaml.colors";

    static final String SAMPLE = String.join("\n",
            "# Sample document", "---", "name: Manuscriptium", "version: 1.2", "enabled: true",
            "tags: [alpha, \"beta\", 'gamma']", "defaults: &defaults", "  retries: 3", "  timeout: ~",
            "service:", "  <<: *defaults", "  description: |", "    Block scalar text",
            "items:", "  - !!str 42", "  - key: value", "...", "");

    private YamlColorScheme() {
    }

    static TokenGroup group(UnaryOperator<String> text) {
        YamlTokenColors defaults = new YamlTokenColors();
        List<TokenDefinition> tokens = new ArrayList<>();
        for (String token : YamlTokens.ALL) {
            tokens.add(new TokenDefinition(token, text.apply("token." + token),
                    defaults.color(token, false).getRGB(), defaults.color(token, true).getRGB()));
        }
        YamlSyntaxHighlighter highlighter = new YamlSyntaxHighlighter();
        return new TokenGroup(GROUP_ID, text.apply("settings.yaml.language"), tokens, SAMPLE,
                sample -> highlighter.highlight(sample).stream().map(YamlColorScheme::span).toList());
    }

    static TokenColors colors(ColorSchemeService service) {
        YamlTokenColors fallback = new YamlTokenColors();
        return (token, dark) -> {
            Color color = service.color(token, dark);
            return color != null ? color : fallback.color(token, dark);
        };
    }

    private static TokenSpan span(HighlightSpan span) {
        return new TokenSpan(span.start(), span.end(), span.token());
    }
}
