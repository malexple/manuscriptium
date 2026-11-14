package ru.mcs.plugin.editorstyle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.CssBorder;
import ru.mcs.spi.editor.style.CssBorders;
import ru.mcs.spi.editor.style.CssDeclaration;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.CssEdges;
import ru.mcs.spi.editor.style.CssLength;
import ru.mcs.spi.editor.style.CssOrigin;
import ru.mcs.spi.editor.style.CssRule;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleElement;
import ru.mcs.spi.editor.style.StyleResolver;
import ru.mcs.spi.editor.style.StyleSheet;
import ru.mcs.spi.editor.style.TextAlign;
import ru.mcs.spi.editor.style.TextDecoration;

/** Каскад и типизированное вычисление стиля без зависимости от Swing-компонентов. */
final class DefaultStyleResolver implements StyleResolver {

    private final Map<String, Selector> selectorCache = new ConcurrentHashMap<>();
    private final Set<String> diagnosticKeys = ConcurrentHashMap.newKeySet();
    private final List<CssDiagnostic> resolutionDiagnostics = new ArrayList<>();

    @Override
    public ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state, StyleSheet sheet) {
        StyleElement target = new StyleElement(state.tag() == null ? tag(kind) : state.tag(),
                state.id(), classes, state.childIndex());
        Match root = match(sheet, StyleElement.of("root"), false, List.of());
        Match element = match(sheet, target, state.hover(), state.ancestors());
        Map<String, String> rawVariables = new HashMap<>(root.variables);
        rawVariables.putAll(element.variables);
        Map<String, String> variables = new LinkedHashMap<>();
        for (String name : rawVariables.keySet()) {
            try {
                variables.put(name, VariableResolver.resolve(rawVariables.get(name), rawVariables));
            } catch (IllegalArgumentException e) {
                diagnose("<computed>", 0, e.getMessage());
            }
        }
        Builder builder = new Builder();
        applyRoot(root.declarations, variables, builder);
        apply(element.declarations, variables, builder, false);
        return builder.build(variables);
    }

    synchronized List<CssDiagnostic> diagnostics() {
        return List.copyOf(resolutionDiagnostics);
    }

    private Match match(StyleSheet sheet, StyleElement target, boolean hover, List<StyleElement> ancestors) {
        List<Matched> declarations = new ArrayList<>();
        Map<String, Matched> variables = new HashMap<>();
        for (CssRule rule : sheet.rules()) {
            int specificity = matchingSpecificity(rule, target, hover, ancestors);
            if (specificity < 0) {
                continue;
            }
            for (int i = 0; i < rule.declarations().size(); i++) {
                CssDeclaration declaration = rule.declarations().get(i);
                Matched matched = new Matched(declaration, specificity, rule.origin(), rule.order(), i);
                if (declaration.name().startsWith("--")) {
                    Matched previous = variables.get(declaration.name());
                    if (previous == null || matched.compareTo(previous) > 0) {
                        variables.put(declaration.name(), matched);
                    }
                } else {
                    declarations.add(matched);
                }
            }
        }
        declarations.sort(Comparator.naturalOrder());
        Map<String, String> resolvedVariables = new HashMap<>();
        variables.forEach((name, value) -> resolvedVariables.put(name, value.declaration().value()));
        return new Match(declarations, resolvedVariables);
    }

    private int matchingSpecificity(CssRule rule, StyleElement target, boolean hover,
                                    List<StyleElement> ancestors) {
        int result = -1;
        for (String source : rule.selectors()) {
            Selector selector = selectorCache.computeIfAbsent(source, Selector::parse);
            if (selector.matches(target, hover, ancestors)) {
                result = Math.max(result, selector.specificity());
            }
        }
        return result;
    }

    private void applyRoot(List<Matched> declarations, Map<String, String> variables, Builder builder) {
        apply(declarations, variables, builder, true);
    }

    private void apply(List<Matched> declarations, Map<String, String> variables,
                       Builder builder, boolean inheritedOnly) {
        for (Matched matched : declarations) {
            CssDeclaration declaration = matched.declaration();
            if (inheritedOnly && !isInherited(declaration.name())) {
                continue;
            }
            try {
                String value = VariableResolver.resolve(declaration.value(), variables);
                apply(declaration.name(), value, builder);
            } catch (IllegalArgumentException e) {
                diagnose("<computed>", declaration.line(),
                        declaration.name() + ": " + e.getMessage());
            }
        }
    }

    private static boolean isInherited(String name) {
        return switch (name) {
            case "color", "font-family", "font-size", "font-weight", "font-style", "line-height",
                    "text-align", "text-decoration", "letter-spacing", "list-style-type" -> true;
            default -> false;
        };
    }

    private static void apply(String name, String value, Builder b) {
        switch (name) {
            case "color" -> b.color = CssValues.color(value);
            case "background", "background-color" -> b.background = "none".equalsIgnoreCase(value)
                    ? new Color(0, true) : CssValues.color(value);
            case "font-family" -> b.fontFamilies = CssValues.fontFamilies(value);
            case "font-size" -> b.fontSize = fontSize(value);
            case "font-weight" -> b.fontWeight = fontWeight(value);
            case "font-style" -> b.italic = !"normal".equalsIgnoreCase(value);
            case "line-height" -> b.lineHeight = "normal".equalsIgnoreCase(value)
                    ? new CssLength(1.5, CssLength.Unit.NUMBER) : CssValues.length(value, true);
            case "text-align" -> b.textAlign = switch (value.toLowerCase(Locale.ROOT)) {
                case "left", "start" -> TextAlign.LEFT;
                case "right", "end" -> TextAlign.RIGHT;
                case "center" -> TextAlign.CENTER;
                case "justify" -> TextAlign.JUSTIFY;
                default -> throw new IllegalArgumentException("Invalid text-align: " + value);
            };
            case "text-decoration" -> b.decorations = decorations(value);
            case "margin" -> b.margin = edges(value);
            case "padding" -> b.padding = edges(value);
            case "margin-top" -> b.margin = with(b.margin, value, 0);
            case "margin-right" -> b.margin = with(b.margin, value, 1);
            case "margin-bottom" -> b.margin = with(b.margin, value, 2);
            case "margin-left" -> b.margin = with(b.margin, value, 3);
            case "padding-top" -> b.padding = with(b.padding, value, 0);
            case "padding-right" -> b.padding = with(b.padding, value, 1);
            case "padding-bottom" -> b.padding = with(b.padding, value, 2);
            case "padding-left" -> b.padding = with(b.padding, value, 3);
            case "border" -> b.borders = borderAll(value, b.color);
            case "border-top" -> b.borders = borderAt(b.borders, value, b.color, 0);
            case "border-right" -> b.borders = borderAt(b.borders, value, b.color, 1);
            case "border-bottom" -> b.borders = borderAt(b.borders, value, b.color, 2);
            case "border-left" -> b.borders = borderAt(b.borders, value, b.color, 3);
            case "border-width" -> b.borders = borderWidths(b.borders, value);
            case "border-style" -> b.borders = borderStyles(b.borders, value);
            case "border-color" -> b.borders = borderColors(b.borders, value);
            case "border-radius" -> b.borderRadius = CssValues.length(value, false);
            case "max-width" -> b.maxWidth = "none".equalsIgnoreCase(value) ? null : CssValues.length(value, false);
            case "width" -> b.width = "auto".equalsIgnoreCase(value) ? null : CssValues.length(value, false);
            case "letter-spacing" -> b.letterSpacing = "normal".equalsIgnoreCase(value)
                    ? CssLength.ZERO : CssValues.length(value, false);
            case "list-style-type" -> b.listStyleType = value.toLowerCase(Locale.ROOT);
            default -> throw new IllegalArgumentException("Unsupported computed property: " + name);
        }
    }

    private static CssLength fontSize(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "medium" -> CssLength.px(16);
            case "small" -> CssLength.px(13);
            case "large" -> CssLength.px(18);
            case "x-large" -> CssLength.px(24);
            case "xx-large" -> CssLength.px(32);
            default -> CssValues.length(value, false);
        };
    }

    private static int fontWeight(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "normal" -> 400;
            case "bold", "bolder" -> 700;
            case "lighter" -> 300;
            default -> {
                int weight = Integer.parseInt(value);
                if (weight < 100 || weight > 900) {
                    throw new IllegalArgumentException("Invalid font-weight: " + value);
                }
                yield weight;
            }
        };
    }

    private static Set<TextDecoration> decorations(String value) {
        Set<TextDecoration> result = EnumSet.noneOf(TextDecoration.class);
        for (String part : CssText.split(value, ' ')) {
            switch (part.toLowerCase(Locale.ROOT)) {
                case "", "none" -> { }
                case "underline" -> result.add(TextDecoration.UNDERLINE);
                case "line-through" -> result.add(TextDecoration.LINE_THROUGH);
                default -> throw new IllegalArgumentException("Invalid text-decoration: " + value);
            }
        }
        return Set.copyOf(result);
    }

    private static CssEdges edges(String value) {
        List<String> parts = CssText.split(value, ' ').stream().filter(s -> !s.isBlank()).toList();
        if (parts.isEmpty() || parts.size() > 4) {
            throw new IllegalArgumentException("Invalid box shorthand: " + value);
        }
        CssLength top = CssValues.length(parts.get(0), false);
        CssLength right = CssValues.length(parts.get(parts.size() > 1 ? 1 : 0), false);
        CssLength bottom = CssValues.length(parts.get(parts.size() > 2 ? 2 : 0), false);
        CssLength left = CssValues.length(parts.get(parts.size() > 3 ? 3 : (parts.size() > 1 ? 1 : 0)), false);
        return new CssEdges(top, right, bottom, left);
    }

    private static CssEdges with(CssEdges old, String value, int side) {
        CssLength length = CssValues.length(value, false);
        return switch (side) {
            case 0 -> new CssEdges(length, old.right(), old.bottom(), old.left());
            case 1 -> new CssEdges(old.top(), length, old.bottom(), old.left());
            case 2 -> new CssEdges(old.top(), old.right(), length, old.left());
            default -> new CssEdges(old.top(), old.right(), old.bottom(), length);
        };
    }

    private static CssBorders borderAll(String value, Color color) {
        CssBorder border = border(value, color);
        return new CssBorders(border, border, border, border);
    }

    private static CssBorders borderAt(CssBorders old, String value, Color color, int side) {
        return put(old, border(value, color), side);
    }

    private static CssBorder border(String value, Color currentColor) {
        CssValues.BorderValue parsed = CssValues.border(value);
        return new CssBorder(parsed.width(), parsed.style(),
                parsed.color() == null ? currentColor : parsed.color());
    }

    private static CssBorders borderWidths(CssBorders old, String value) {
        CssEdges widths = edges(value);
        CssBorders result = put(old, new CssBorder(widths.top(), old.top().style(), old.top().color()), 0);
        result = put(result, new CssBorder(widths.right(), result.right().style(), result.right().color()), 1);
        result = put(result, new CssBorder(widths.bottom(), result.bottom().style(), result.bottom().color()), 2);
        return put(result, new CssBorder(widths.left(), result.left().style(), result.left().color()), 3);
    }

    private static CssBorders borderStyles(CssBorders old, String value) {
        List<String> values = fourValues(value);
        CssBorders result = put(old, new CssBorder(old.top().width(), values.get(0), old.top().color()), 0);
        result = put(result, new CssBorder(result.right().width(), values.get(1), result.right().color()), 1);
        result = put(result, new CssBorder(result.bottom().width(), values.get(2), result.bottom().color()), 2);
        return put(result, new CssBorder(result.left().width(), values.get(3), result.left().color()), 3);
    }

    private static CssBorders borderColors(CssBorders old, String value) {
        List<String> values = fourValues(value);
        CssBorders result = put(old, new CssBorder(old.top().width(), old.top().style(), CssValues.color(values.get(0))), 0);
        result = put(result, new CssBorder(result.right().width(), result.right().style(), CssValues.color(values.get(1))), 1);
        result = put(result, new CssBorder(result.bottom().width(), result.bottom().style(), CssValues.color(values.get(2))), 2);
        return put(result, new CssBorder(result.left().width(), result.left().style(), CssValues.color(values.get(3))), 3);
    }

    private static List<String> fourValues(String value) {
        List<String> p = CssText.split(value, ' ').stream().filter(s -> !s.isBlank()).toList();
        if (p.isEmpty() || p.size() > 4) {
            throw new IllegalArgumentException("Invalid four-sided value: " + value);
        }
        return List.of(p.get(0), p.get(p.size() > 1 ? 1 : 0), p.get(p.size() > 2 ? 2 : 0),
                p.get(p.size() > 3 ? 3 : (p.size() > 1 ? 1 : 0)));
    }

    private static CssBorders put(CssBorders old, CssBorder border, int side) {
        return switch (side) {
            case 0 -> new CssBorders(border, old.right(), old.bottom(), old.left());
            case 1 -> new CssBorders(old.top(), border, old.bottom(), old.left());
            case 2 -> new CssBorders(old.top(), old.right(), border, old.left());
            default -> new CssBorders(old.top(), old.right(), old.bottom(), border);
        };
    }

    private synchronized void diagnose(String source, int line, String message) {
        if (diagnosticKeys.add(source + ":" + line + ":" + message)) {
            resolutionDiagnostics.add(CssDiagnostic.warning(source, line, message));
        }
    }

    private static String tag(BlockKind kind) {
        return switch (kind) {
            case HEADING -> "h1";
            case PARAGRAPH, TEXT -> "p";
            case BLOCK_QUOTE, CALLOUT -> "blockquote";
            case BULLET_LIST -> "ul";
            case ORDERED_LIST -> "ol";
            case LIST_ITEM, TASK_ITEM -> "li";
            case FENCED_CODE, INDENTED_CODE, MATH_BLOCK -> "pre";
            case THEMATIC_BREAK -> "hr";
            case TABLE, TABLE_HEAD, TABLE_BODY, TABLE_SEPARATOR -> "table";
            case TABLE_ROW -> "tr";
            case TABLE_CELL -> "td";
            case STRONG_EMPHASIS -> "strong";
            case EMPHASIS -> "em";
            case STRIKETHROUGH -> "del";
            case CODE -> "code";
            case LINK, LINK_REFERENCE, AUTOLINK, WIKILINK -> "a";
            case IMAGE, IMAGE_REFERENCE, WIKI_IMAGE, EMBED -> "img";
            default -> "div";
        };
    }

    private record Matched(CssDeclaration declaration, int specificity, CssOrigin origin,
                           int order, int declarationIndex) implements Comparable<Matched> {
        @Override
        public int compareTo(Matched other) {
            // Порядок сортировки — от слабого к сильному; позднее применение перезаписывает раннее.
            int result = Boolean.compare(declaration.important(), other.declaration.important());
            if (result == 0) result = Integer.compare(specificity, other.specificity);
            if (result == 0) result = Integer.compare(origin.ordinal(), other.origin.ordinal());
            if (result == 0) result = Integer.compare(order, other.order);
            if (result == 0) result = Integer.compare(declarationIndex, other.declarationIndex);
            return result;
        }
    }

    private record Match(List<Matched> declarations, Map<String, String> variables) {
    }

    private static final class Builder {
        private Color color = new Color(0x24292f);
        private Color background = new Color(0, true);
        private List<String> fontFamilies = List.of("SansSerif");
        private CssLength fontSize = CssLength.px(16);
        private int fontWeight = 400;
        private boolean italic;
        private CssLength lineHeight = new CssLength(1.5, CssLength.Unit.NUMBER);
        private TextAlign textAlign = TextAlign.LEFT;
        private Set<TextDecoration> decorations = Set.of();
        private CssEdges margin = CssEdges.ZERO;
        private CssEdges padding = CssEdges.ZERO;
        private CssBorders borders = CssBorders.NONE;
        private CssLength borderRadius = CssLength.ZERO;
        private CssLength maxWidth;
        private CssLength width;
        private CssLength letterSpacing = CssLength.ZERO;
        private String listStyleType = "disc";

        private ComputedStyle build(Map<String, String> variables) {
            return new ComputedStyle(color, background, fontFamilies, fontSize, fontWeight, italic,
                    lineHeight, textAlign, decorations, margin, padding, borders, borderRadius,
                    maxWidth, width, letterSpacing, listStyleType, variables);
        }
    }
}
