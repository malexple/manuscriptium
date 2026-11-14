package ru.mcs.spi.editor.style;

import java.util.List;

/** Полный неизменяемый snapshot темы, включая нефатальные диагностики. */
public final class StyleSheet {

    private static final StyleSheet EMPTY = new StyleSheet(List.of(), List.of(), List.of());

    private final List<CssRule> rules;
    private final List<FontFaceDefinition> fontFaces;
    private final List<CssDiagnostic> diagnostics;

    public StyleSheet(List<CssRule> rules, List<FontFaceDefinition> fontFaces,
                      List<CssDiagnostic> diagnostics) {
        this.rules = List.copyOf(rules);
        this.fontFaces = List.copyOf(fontFaces);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static StyleSheet empty() {
        return EMPTY;
    }

    public List<CssRule> rules() {
        return rules;
    }

    public List<FontFaceDefinition> fontFaces() {
        return fontFaces;
    }

    public List<CssDiagnostic> diagnostics() {
        return diagnostics;
    }
}
