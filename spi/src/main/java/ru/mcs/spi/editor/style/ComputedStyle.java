package ru.mcs.spi.editor.style;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Типизированный результат CSS-каскада, готовый для раскладки E5. */
public record ComputedStyle(Color color, Color backgroundColor, List<String> fontFamilies,
                            CssLength fontSize, int fontWeight, boolean italic,
                            CssLength lineHeight, TextAlign textAlign,
                            Set<TextDecoration> textDecorations, CssEdges margin, CssEdges padding,
                            CssBorders borders, CssLength borderRadius, CssLength maxWidth,
                            CssLength width, CssLength letterSpacing, String listStyleType,
                            Map<String, String> variables) {

    public ComputedStyle {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(backgroundColor, "backgroundColor");
        fontFamilies = List.copyOf(fontFamilies);
        Objects.requireNonNull(fontSize, "fontSize");
        Objects.requireNonNull(lineHeight, "lineHeight");
        Objects.requireNonNull(textAlign, "textAlign");
        textDecorations = Set.copyOf(textDecorations);
        Objects.requireNonNull(margin, "margin");
        Objects.requireNonNull(padding, "padding");
        Objects.requireNonNull(borders, "borders");
        Objects.requireNonNull(borderRadius, "borderRadius");
        Objects.requireNonNull(letterSpacing, "letterSpacing");
        Objects.requireNonNull(listStyleType, "listStyleType");
        variables = Map.copyOf(variables);
    }

    public static ComputedStyle defaults() {
        return new ComputedStyle(new Color(0x24292f), new Color(0, true),
                List.of("SansSerif"), CssLength.px(16), 400, false,
                new CssLength(1.5, CssLength.Unit.NUMBER), TextAlign.LEFT, Set.of(),
                CssEdges.ZERO, CssEdges.ZERO, CssBorders.NONE, CssLength.ZERO,
                null, null, CssLength.ZERO, "disc", Map.of());
    }

    public double fontSizePx() {
        return fontSize.toPx(16, 16);
    }

    public double lineHeightPx() {
        return lineHeight.toPx(fontSizePx(), 16);
    }
}
