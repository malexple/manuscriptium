package ru.mcs.yaml;

import java.awt.Color;
import java.util.Map;
import ru.mcs.editor.view.TokenColors;

/** Палитра подсветки YAML по умолчанию: пары цветов для светлого и тёмного фона. */
final class YamlTokenColors implements TokenColors {

    private static final Map<String, int[]> PALETTE = Map.of(
            YamlTokens.KEY, new int[]{0x1A4D9C, 0x6CB6FF},
            YamlTokens.STRING, new int[]{0x067D17, 0x7EE787},
            YamlTokens.NUMBER, new int[]{0x1750EB, 0x79C0FF},
            YamlTokens.CONSTANT, new int[]{0x871094, 0xD2A8FF},
            YamlTokens.COMMENT, new int[]{0x8C8C8C, 0x8B949E},
            YamlTokens.ANCHOR, new int[]{0xCC7832, 0xFFA657},
            YamlTokens.TAG, new int[]{0x9E880D, 0xE3B341},
            YamlTokens.MARKER, new int[]{0x6A737D, 0x8B949E},
            YamlTokens.PUNCT, new int[]{0x6A737D, 0x8B949E});

    @Override
    public Color color(String token, boolean darkBackground) {
        int[] pair = PALETTE.get(token);
        return pair == null ? null : new Color(pair[darkBackground ? 1 : 0]);
    }
}
