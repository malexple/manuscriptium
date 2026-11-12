package ru.mcs.plugin.notes;

/** Статистика исходного Markdown. Символы — UTF-16 code units, как в Swing Document. */
record NoteStatistics(int words, int characters, int lines) {

    static NoteStatistics count(String text) {
        if (text.isEmpty()) {
            return new NoteStatistics(0, 0, 0);
        }
        int words = 0;
        boolean inWord = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            boolean wordPart = Character.isLetterOrDigit(codePoint) || codePoint == '_';
            if (wordPart && !inWord) {
                words++;
            }
            inWord = wordPart;
            offset += Character.charCount(codePoint);
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return new NoteStatistics(words, text.length(), lines);
    }
}
