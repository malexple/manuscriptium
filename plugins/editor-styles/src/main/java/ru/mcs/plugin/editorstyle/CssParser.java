package ru.mcs.plugin.editorstyle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.mcs.spi.editor.style.CssDeclaration;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.CssOrigin;
import ru.mcs.spi.editor.style.CssRule;
import ru.mcs.spi.editor.style.FontFaceDefinition;

/** Отказоустойчивый парсер согласованного CSS-подмножества Typora. */
final class CssParser {

    private static final Pattern IMPORTANT = Pattern.compile("(?i)\\s*!important\\s*$");
    private static final Pattern URL = Pattern.compile("(?i)url\\(\\s*([^)]*?)\\s*\\)");
    private static final Set<String> SUPPORTED = Set.of(
            "color", "background", "background-color", "font-family", "font-size", "font-weight",
            "font-style", "line-height", "text-align", "text-decoration", "margin", "padding",
            "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding-top", "padding-right", "padding-bottom", "padding-left",
            "border", "border-top", "border-right", "border-bottom", "border-left",
            "border-width", "border-style", "border-color", "border-radius", "max-width", "width",
            "letter-spacing", "list-style-type");

    record ParseResult(List<CssRule> rules, List<FontFaceDefinition> fontFaces,
                       List<CssDiagnostic> diagnostics, int nextOrder) {
    }

    ParseResult parse(String input, String source, Path baseDirectory, CssOrigin origin, int firstOrder) {
        String css = CssText.stripComments(input);
        List<CssRule> rules = new ArrayList<>();
        List<FontFaceDefinition> fonts = new ArrayList<>();
        List<CssDiagnostic> diagnostics = new ArrayList<>();
        int order = firstOrder;
        int i = 0;
        while (i < css.length()) {
            while (i < css.length() && (Character.isWhitespace(css.charAt(i)) || css.charAt(i) == ';')) {
                i++;
            }
            if (i >= css.length()) {
                break;
            }
            int start = i;
            int open = findTopLevel(css, i, '{', ';');
            if (open < 0) {
                diagnostics.add(CssDiagnostic.warning(source, CssText.lineAt(css, start),
                        "Unclosed or incomplete rule: " + css.substring(start).trim()));
                break;
            }
            char terminator = css.charAt(open);
            String header = css.substring(start, open).trim();
            int line = CssText.lineAt(css, start);
            if (terminator == ';') {
                handleStatementAtRule(header, source, line, diagnostics);
                i = open + 1;
                continue;
            }
            int close = matchingBrace(css, open);
            if (close < 0) {
                diagnostics.add(CssDiagnostic.warning(source, line, "Missing closing brace for " + header));
                break;
            }
            String body = css.substring(open + 1, close);
            if (header.startsWith("@")) {
                if (header.toLowerCase(Locale.ROOT).startsWith("@font-face")) {
                    parseFontFace(body, source, line, diagnostics).ifPresent(fonts::add);
                } else {
                    diagnostics.add(CssDiagnostic.warning(source, line,
                            "Unsupported at-rule ignored: " + header));
                }
            } else if (!header.isEmpty()) {
                List<String> selectors = new ArrayList<>();
                for (String selector : CssText.split(header, ',')) {
                    if (selector.isEmpty()) {
                        continue;
                    }
                    try {
                        Selector.parse(selector);
                        selectors.add(selector);
                    } catch (IllegalArgumentException e) {
                        diagnostics.add(CssDiagnostic.warning(source, line,
                                "Unsupported selector ignored: " + selector + " (" + e.getMessage() + ")"));
                    }
                }
                List<CssDeclaration> declarations = declarations(body, source, line, diagnostics, false);
                if (selectors.isEmpty()) {
                    diagnostics.add(CssDiagnostic.warning(source, line, "Rule without selectors ignored"));
                } else if (!declarations.isEmpty()) {
                    rules.add(new CssRule(selectors, declarations, origin, order++));
                }
            }
            i = close + 1;
        }
        return new ParseResult(List.copyOf(rules), List.copyOf(fonts), List.copyOf(diagnostics), order);
    }

    private List<CssDeclaration> declarations(String body, String source, int ruleLine,
                                               List<CssDiagnostic> diagnostics, boolean fontFace) {
        List<CssDeclaration> result = new ArrayList<>();
        for (String item : CssText.split(body, ';')) {
            if (item.isBlank()) {
                continue;
            }
            int colon = CssText.topLevelColon(item);
            if (colon < 0) {
                diagnostics.add(CssDiagnostic.warning(source, ruleLine,
                        "Declaration without ':' ignored: " + item));
                continue;
            }
            String name = item.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = item.substring(colon + 1).trim();
            Matcher important = IMPORTANT.matcher(value);
            boolean isImportant = important.find();
            if (isImportant) {
                value = value.substring(0, important.start()).trim();
            }
            if (value.isEmpty()) {
                diagnostics.add(CssDiagnostic.warning(source, ruleLine, "Empty value for " + name));
                continue;
            }
            if (!fontFace && !name.startsWith("--") && !SUPPORTED.contains(name)) {
                diagnostics.add(CssDiagnostic.warning(source, ruleLine,
                        "Unsupported property ignored: " + name));
                continue;
            }
            if (value.toLowerCase(Locale.ROOT).contains("calc(")) {
                diagnostics.add(CssDiagnostic.warning(source, ruleLine,
                        "calc() is not supported: " + name));
                continue;
            }
            if (!fontFace && CssText.externalUrl(value)) {
                diagnostics.add(CssDiagnostic.warning(source, ruleLine,
                        "External URL ignored: " + name));
                continue;
            }
            result.add(new CssDeclaration(name, value, isImportant, ruleLine));
        }
        return result;
    }

    private java.util.Optional<FontFaceDefinition> parseFontFace(String body, String source, int line,
                                                                  List<CssDiagnostic> diagnostics) {
        List<CssDeclaration> declarations = declarations(body, source, line, diagnostics, true);
        String family = null;
        String url = null;
        int weight = 400;
        boolean italic = false;
        for (CssDeclaration declaration : declarations) {
            switch (declaration.name()) {
                case "font-family" -> family = CssText.unquote(declaration.value());
                case "src" -> {
                    Matcher matcher = URL.matcher(declaration.value());
                    if (matcher.find()) {
                        url = CssText.unquote(matcher.group(1));
                    }
                }
                case "font-weight" -> weight = switch (declaration.value().toLowerCase(Locale.ROOT)) {
                    case "normal" -> 400;
                    case "bold" -> 700;
                    default -> parseWeight(declaration.value());
                };
                case "font-style" -> italic = declaration.value().toLowerCase(Locale.ROOT).contains("italic");
                default -> diagnostics.add(CssDiagnostic.warning(source, line,
                        "Unsupported @font-face property ignored: " + declaration.name()));
            }
        }
        if (family == null || url == null || CssText.externalUrl(url)) {
            diagnostics.add(CssDiagnostic.warning(source, line,
                    "@font-face requires a local font-family and src url()"));
            return java.util.Optional.empty();
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
            diagnostics.add(CssDiagnostic.warning(source, line,
                    "Only local TTF/OTF fonts are supported: " + url));
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new FontFaceDefinition(family, url, weight, italic));
    }

    private static int parseWeight(String value) {
        try {
            return Math.max(100, Math.min(900, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return 400;
        }
    }

    private static void handleStatementAtRule(String header, String source, int line,
                                              List<CssDiagnostic> diagnostics) {
        String lower = header.toLowerCase(Locale.ROOT);
        if (lower.startsWith("@charset")) {
            return;
        }
        diagnostics.add(CssDiagnostic.warning(source, line,
                lower.startsWith("@import") ? "@import is disabled; load local theme files explicitly"
                        : "Unsupported at-rule ignored: " + header));
    }

    private static int findTopLevel(String css, int start, char block, char statement) {
        int parentheses = 0;
        char quote = 0;
        for (int i = start; i < css.length(); i++) {
            char c = css.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                parentheses++;
            } else if (c == ')' && parentheses > 0) {
                parentheses--;
            } else if (parentheses == 0 && (c == block || c == statement)) {
                return i;
            }
        }
        return -1;
    }

    private static int matchingBrace(String css, int open) {
        int depth = 1;
        char quote = 0;
        for (int i = open + 1; i < css.length(); i++) {
            char c = css.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
