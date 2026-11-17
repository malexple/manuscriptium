package ru.mcs.markdown.editor.action;

import ru.mcs.editor.model.Range;

/** Unicode-safe расширение выделения: слово → соседние слова → вся physical line без перевода строки. */
public final class SelectionExpander {

    private SelectionExpander() {
    }

    public static Range wordAt(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return new Range(0, 0);
        }
        int safe = clamp(offset, 0, text.length());
        if (safe < text.length() && isWord(Character.codePointAt(text, safe))) {
            return wordRange(text, safe);
        }
        if (safe > 0 && isWord(Character.codePointBefore(text, safe))) {
            return wordRange(text, safe - Character.charCount(Character.codePointBefore(text, safe)));
        }
        return new Range(safe, safe);
    }

    public static Range extend(String text, Range selection, int caret) {
        if (text == null || text.isEmpty()) {
            return new Range(0, 0);
        }
        int safeCaret = clamp(caret, 0, text.length());
        int lineStart = lineStart(text, safeCaret);
        int lineEnd = lineEnd(text, safeCaret);
        if (selection.length() == 0) {
            Range word = wordAt(text, safeCaret);
            return word.length() > 0 ? word : new Range(lineStart, lineEnd);
        }

        int start = clamp(selection.start(), lineStart, lineEnd);
        int end = clamp(selection.end(), start, lineEnd);
        if (start == lineStart && end == lineEnd) {
            return new Range(lineStart, lineEnd);
        }

        // Произвольное ручное выделение сначала выравнивается по границам слов.
        int wordStart = start > lineStart && start < lineEnd
                && isWord(Character.codePointBefore(text, start)) && isWord(Character.codePointAt(text, start))
                ? wordRange(text, start).start() : start;
        int wordEnd = end > lineStart && end < lineEnd
                && isWord(Character.codePointBefore(text, end)) && isWord(Character.codePointAt(text, end))
                ? wordRange(text, end).end() : end;
        if (wordStart != start || wordEnd != end) {
            return new Range(wordStart, wordEnd);
        }

        int right = end;
        while (right < lineEnd) {
            int codePoint = Character.codePointAt(text, right);
            if (isWord(codePoint)) {
                return new Range(start, wordRange(text, right).end());
            }
            right += Character.charCount(codePoint);
        }

        int left = start;
        while (left > lineStart) {
            int codePoint = Character.codePointBefore(text, left);
            int previous = left - Character.charCount(codePoint);
            if (isWord(codePoint)) {
                return new Range(wordRange(text, previous).start(), end);
            }
            left = previous;
        }
        return new Range(lineStart, lineEnd);
    }

    private static Range wordRange(String text, int offset) {
        int start = offset;
        int end = offset + Character.charCount(Character.codePointAt(text, offset));
        while (start > 0) {
            int previous = Character.codePointBefore(text, start);
            if (!isWord(previous)) {
                break;
            }
            start -= Character.charCount(previous);
        }
        while (end < text.length()) {
            int next = Character.codePointAt(text, end);
            if (!isWord(next)) {
                break;
            }
            end += Character.charCount(next);
        }
        return new Range(start, end);
    }

    private static int lineStart(String text, int offset) {
        int start = offset;
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        return start;
    }

    private static int lineEnd(String text, int offset) {
        int end = offset;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        return end;
    }

    private static boolean isWord(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_';
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
