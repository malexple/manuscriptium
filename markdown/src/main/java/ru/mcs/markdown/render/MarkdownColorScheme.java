package ru.mcs.markdown.render;

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

/** Подключает токены подсветки Markdown к странице «Цветовая схема» и к рисованию вкладки «Исходник». */
public final class MarkdownColorScheme {

    public static final String GROUP_ID = "markdown.colors";

    private static final String SAMPLE = String.join("\n",
            "---", "title: Sample note", "---", "# Heading", "",
            "Some **bold**, *italic* and ~~struck~~ text with `code`.", "",
            "> Quote with a [link](https://example.com) and [[Wiki link]] and a note[^1]", "",
            "- [x] Done task", "- List item", "", "---", "",
            "| Column | Value |", "|--------|-------|", "| a      | 1     |", "",
            "```java", "int x = 1;", "```", "",
            "Formula $E = mc^2$, <b>tag</b> and <!-- comment -->", "");

    private MarkdownColorScheme() {
    }

    /** Группа токенов; названия берутся из сообщений по ключам {@code token.<id>}. */
    public static TokenGroup group(UnaryOperator<String> text) {
        MarkdownTokenColors defaults = new MarkdownTokenColors();
        List<TokenDefinition> tokens = new ArrayList<>();
        for (String token : MarkdownTokens.ALL) {
            tokens.add(new TokenDefinition(token, text.apply("token." + token),
                    defaults.color(token, false).getRGB(), defaults.color(token, true).getRGB()));
        }
        MarkdownSyntaxHighlighter highlighter = new MarkdownSyntaxHighlighter();
        return new TokenGroup(GROUP_ID, text.apply("settings.markdown.language"), tokens, SAMPLE,
                sample -> highlighter.highlight(sample).stream().map(MarkdownColorScheme::span).toList());
    }

    /** Цвета подсветки из общего сервиса; для токенов, о которых сервис не знает, остаётся палитра по умолчанию. */
    public static TokenColors colors(ColorSchemeService service) {
        MarkdownTokenColors fallback = new MarkdownTokenColors();
        return (token, dark) -> {
            Color color = service.color(token, dark);
            return color != null ? color : fallback.color(token, dark);
        };
    }

    private static TokenSpan span(HighlightSpan span) {
        return new TokenSpan(span.start(), span.end(), span.token());
    }
}
