package ru.mcs.core.theme;

import java.awt.Color;
import javax.swing.plaf.ColorUIResource;

/** Преобразование JSON-значений в значения UIDefaults. */
final class ThemeTokens {

    private ThemeTokens() {
    }

    static Object toUiValue(Object value) {
        if (value instanceof String text && text.startsWith("#")) {
            return new ColorUIResource(parseColor(text));
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        throw new IllegalArgumentException("Theme token must be a string, number or boolean, got: " + value);
    }

    static Color parseColor(String text) {
        String hex = text.length() > 0 && text.charAt(0) == '#' ? text.substring(1) : text;
        try {
            return switch (hex.length()) {
                case 6 -> new Color(Integer.parseInt(hex, 16));
                case 8 -> {
                    int rgba = (int) Long.parseLong(hex, 16);
                    yield new Color((rgba >>> 24) & 0xff, (rgba >>> 16) & 0xff,
                            (rgba >>> 8) & 0xff, rgba & 0xff);
                }
                default -> throw new IllegalArgumentException(
                        "Color must use #RRGGBB or #RRGGBBAA: " + text);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid color: " + text, e);
        }
    }
}
