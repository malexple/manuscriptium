package ru.mcs.markdown.render;

import java.awt.Color;
import java.util.Map;
import ru.mcs.editor.view.TokenColors;

/** Палитра подсветки Markdown по умолчанию: пары цветов для светлого и тёмного фона. */
public final class MarkdownTokenColors implements TokenColors {

    private static final Map<String, int[]> PALETTE = Map.ofEntries(
            Map.entry(MarkdownTokens.HEADING, new int[]{0x1A4D9C, 0x6CB6FF}),
            Map.entry(MarkdownTokens.HEADING_MARKER, new int[]{0x1A4D9C, 0x6CB6FF}),
            Map.entry(MarkdownTokens.BOLD, new int[]{0x7B1FA2, 0xD2A8FF}),
            Map.entry(MarkdownTokens.ITALIC, new int[]{0x7B1FA2, 0xD2A8FF}),
            Map.entry(MarkdownTokens.STRIKE, new int[]{0x888888, 0x8B949E}),
            Map.entry(MarkdownTokens.CODE, new int[]{0x067D17, 0x7EE787}),
            Map.entry(MarkdownTokens.FENCE, new int[]{0x067D17, 0x7EE787}),
            Map.entry(MarkdownTokens.CODE_BLOCK, new int[]{0x2E7D32, 0xA5D6A7}),
            Map.entry(MarkdownTokens.LINK_TEXT, new int[]{0x0B6ACB, 0x58A6FF}),
            Map.entry(MarkdownTokens.LINK_URL, new int[]{0x2A7FA5, 0x79C0FF}),
            Map.entry(MarkdownTokens.WIKILINK, new int[]{0x0B6ACB, 0x58A6FF}),
            Map.entry(MarkdownTokens.FOOTNOTE, new int[]{0x0B6ACB, 0x58A6FF}),
            Map.entry(MarkdownTokens.LIST, new int[]{0xCC7832, 0xFFA657}),
            Map.entry(MarkdownTokens.TASK, new int[]{0xCC7832, 0xFFA657}),
            Map.entry(MarkdownTokens.QUOTE, new int[]{0x6A737D, 0x8B949E}),
            Map.entry(MarkdownTokens.HR, new int[]{0x6A737D, 0x8B949E}),
            Map.entry(MarkdownTokens.TABLE, new int[]{0x6A737D, 0x8B949E}),
            Map.entry(MarkdownTokens.HTML, new int[]{0x0033B3, 0x79C0FF}),
            Map.entry(MarkdownTokens.COMMENT, new int[]{0x8C8C8C, 0x8B949E}),
            Map.entry(MarkdownTokens.FRONT_MATTER, new int[]{0x8250DF, 0xD2A8FF}),
            Map.entry(MarkdownTokens.MATH, new int[]{0x953800, 0xFFA657}));

    @Override
    public Color color(String token, boolean darkBackground) {
        int[] pair = PALETTE.get(token);
        return pair == null ? null : new Color(pair[darkBackground ? 1 : 0]);
    }
}
