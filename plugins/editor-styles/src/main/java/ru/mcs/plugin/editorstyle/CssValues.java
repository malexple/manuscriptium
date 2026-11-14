package ru.mcs.plugin.editorstyle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.mcs.spi.editor.style.CssLength;

/** Парсинг типизированных значений после раскрытия переменных. */
final class CssValues {

    private static final Pattern LENGTH = Pattern.compile(
            "(?i)^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(px|em|rem|%|pt)?$");
    private static final Set<String> BORDER_STYLES = Set.of("none", "solid", "dashed", "dotted", "double");
    private static final Map<String, Color> NAMED = Map.ofEntries(
            Map.entry("black", Color.BLACK), Map.entry("white", Color.WHITE),
            Map.entry("red", Color.RED), Map.entry("green", new Color(0, 128, 0)),
            Map.entry("blue", Color.BLUE), Map.entry("gray", new Color(128, 128, 128)),
            Map.entry("grey", new Color(128, 128, 128)),
            Map.entry("lightgray", new Color(211, 211, 211)),
            Map.entry("lightgrey", new Color(211, 211, 211)),
            Map.entry("darkgray", new Color(169, 169, 169)),
            Map.entry("darkgrey", new Color(169, 169, 169)),
            Map.entry("orange", Color.ORANGE), Map.entry("yellow", Color.YELLOW),
            Map.entry("purple", new Color(128, 0, 128)), Map.entry("pink", Color.PINK),
            Map.entry("brown", new Color(165, 42, 42)), Map.entry("cyan", Color.CYAN),
            Map.entry("magenta", Color.MAGENTA), Map.entry("rebeccapurple", new Color(102, 51, 153)),
            Map.entry("transparent", new Color(0, true)));

    record BorderValue(CssLength width, String style, Color color) {
    }

    private CssValues() {
    }

    static CssLength length(String value, boolean unitlessNumber) {
        Matcher matcher = LENGTH.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid CSS length: " + value);
        }
        double number = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null) {
            return unitlessNumber || number == 0
                    ? new CssLength(number, unitlessNumber ? CssLength.Unit.NUMBER : CssLength.Unit.PX)
                    : fail(value);
        }
        return new CssLength(number, switch (unit.toLowerCase(Locale.ROOT)) {
            case "px" -> CssLength.Unit.PX;
            case "em" -> CssLength.Unit.EM;
            case "rem" -> CssLength.Unit.REM;
            case "%" -> CssLength.Unit.PERCENT;
            case "pt" -> CssLength.Unit.PT;
            default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
        });
    }

    static Color color(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (NAMED.containsKey(lower)) {
            return NAMED.get(lower);
        }
        if (lower.startsWith("#")) {
            return hex(lower);
        }
        if (lower.startsWith("rgb(") || lower.startsWith("rgba(")) {
            return rgb(lower);
        }
        if (lower.startsWith("hsl(") || lower.startsWith("hsla(")) {
            return hsl(lower);
        }
        throw new IllegalArgumentException("Invalid color: " + value);
    }

    static List<String> fontFamilies(String value) {
        List<String> result = new ArrayList<>();
        for (String family : CssText.split(value, ',')) {
            if (!family.isBlank()) {
                result.add(CssText.unquote(family));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Empty font-family");
        }
        return List.copyOf(result);
    }

    static BorderValue border(String value) {
        CssLength width = CssLength.px(3);
        String style = "none";
        Color color = null;
        for (String part : CssText.split(value, ' ')) {
            if (part.isBlank()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (BORDER_STYLES.contains(lower)) {
                style = lower;
            } else {
                try {
                    width = length(part, false);
                } catch (IllegalArgumentException notLength) {
                    color = color(part);
                }
            }
        }
        return new BorderValue(width, style, color);
    }

    private static CssLength fail(String value) {
        throw new IllegalArgumentException("Unitless non-zero length: " + value);
    }

    private static Color hex(String value) {
        String hex = value.substring(1);
        try {
            if (hex.length() == 3 || hex.length() == 4) {
                int r = Integer.parseInt(hex.substring(0, 1).repeat(2), 16);
                int g = Integer.parseInt(hex.substring(1, 2).repeat(2), 16);
                int b = Integer.parseInt(hex.substring(2, 3).repeat(2), 16);
                int a = hex.length() == 4 ? Integer.parseInt(hex.substring(3, 4).repeat(2), 16) : 255;
                return new Color(r, g, b, a);
            }
            if (hex.length() == 6 || hex.length() == 8) {
                long rgb = Long.parseLong(hex, 16);
                if (hex.length() == 6) {
                    return new Color((int) rgb, false);
                }
                return new Color((int) (rgb >> 24 & 0xff), (int) (rgb >> 16 & 0xff),
                        (int) (rgb >> 8 & 0xff), (int) (rgb & 0xff));
            }
        } catch (NumberFormatException ignored) {
            // Единое сообщение ниже.
        }
        throw new IllegalArgumentException("Invalid hex color: " + value);
    }

    private static Color rgb(String value) {
        List<String> parts = functionParts(value);
        if (parts.size() < 3) {
            throw new IllegalArgumentException("Invalid rgb(): " + value);
        }
        int r = channel(parts.get(0));
        int g = channel(parts.get(1));
        int b = channel(parts.get(2));
        int a = parts.size() > 3 ? alpha(parts.get(3)) : 255;
        return new Color(r, g, b, a);
    }

    private static Color hsl(String value) {
        List<String> parts = functionParts(value);
        if (parts.size() < 3) {
            throw new IllegalArgumentException("Invalid hsl(): " + value);
        }
        float h = Float.parseFloat(parts.get(0).replace("deg", "")) / 360f;
        float s = percent(parts.get(1));
        float l = percent(parts.get(2));
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        int r = Math.round(hue(p, q, h + 1f / 3f) * 255);
        int g = Math.round(hue(p, q, h) * 255);
        int b = Math.round(hue(p, q, h - 1f / 3f) * 255);
        int a = parts.size() > 3 ? alpha(parts.get(3)) : 255;
        return new Color(r, g, b, a);
    }

    private static float hue(float p, float q, float t) {
        t = (t % 1 + 1) % 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private static List<String> functionParts(String value) {
        int open = value.indexOf('(');
        int close = value.lastIndexOf(')');
        if (open < 0 || close <= open) {
            throw new IllegalArgumentException("Invalid function: " + value);
        }
        String body = value.substring(open + 1, close).replace('/', ' ');
        return CssText.split(body.replace(',', ' '), ' ').stream().filter(s -> !s.isBlank()).toList();
    }

    private static int channel(String value) {
        return Math.max(0, Math.min(255, value.endsWith("%")
                ? Math.round(percent(value) * 255) : Integer.parseInt(value)));
    }

    private static int alpha(String value) {
        float alpha = value.endsWith("%") ? percent(value) : Float.parseFloat(value);
        return Math.max(0, Math.min(255, Math.round(alpha * 255)));
    }

    private static float percent(String value) {
        if (!value.endsWith("%")) {
            throw new IllegalArgumentException("Percentage expected: " + value);
        }
        return Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
    }
}
