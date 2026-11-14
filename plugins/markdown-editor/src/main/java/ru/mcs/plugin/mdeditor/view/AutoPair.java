package ru.mcs.plugin.mdeditor.view;

import java.util.Map;

/** Непрозрачные правила Auto Pair; не знает о Swing и DocumentModel. */
final class AutoPair {

    private static final Map<Character, Character> PAIRS = Map.of(
            '(', ')', '[', ']', '{', '}', '"', '"', '\'', '\'', '`', '`', '*', '*', '_', '_');

    private AutoPair() {
    }

    static Character closingFor(char opening) {
        return PAIRS.get(opening);
    }

    static boolean isOpening(char ch) {
        return PAIRS.containsKey(ch);
    }

    static boolean isClosing(char ch) {
        return PAIRS.containsValue(ch);
    }

    static boolean isEmptyPair(CharSequence text, int caret) {
        return caret > 0 && caret < text.length()
                && closingFor(text.charAt(caret - 1)) != null
                && closingFor(text.charAt(caret - 1)) == text.charAt(caret);
    }
}
