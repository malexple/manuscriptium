package ru.mcs.plugin.mdeditor.action;

import java.text.BreakIterator;
import java.util.Locale;

/** Подсчёт для выделения или всего файла; offsets остаются UTF-16, символы считаются Unicode code points. */
public final class WordCount {

    private static final double WORDS_PER_MINUTE = 200.0;

    private WordCount() {
    }

    public static Stats count(String text) {
        String value = text == null ? "" : text;
        int words = 0;
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.ROOT);
        iterator.setText(value);
        for (int start = iterator.first(), end = iterator.next(); end != BreakIterator.DONE;
             start = end, end = iterator.next()) {
            if (value.substring(start, end).codePoints().anyMatch(Character::isLetterOrDigit)) {
                words++;
            }
        }
        int lines = value.isEmpty() ? 0 : 1 + (int) value.chars().filter(ch -> ch == '\n').count();
        int characters = value.codePointCount(0, value.length());
        int readingMinutes = words == 0 ? 0 : Math.max(1, (int) Math.ceil(words / WORDS_PER_MINUTE));
        return new Stats(words, characters, lines, readingMinutes);
    }

    public record Stats(int words, int characters, int lines, int readingMinutes) {
    }
}
