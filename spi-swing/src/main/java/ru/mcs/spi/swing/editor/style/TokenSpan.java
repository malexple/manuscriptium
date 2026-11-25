package ru.mcs.spi.swing.editor.style;

/** Участок образца текста [start, end) с идентификатором токена. */
public record TokenSpan(int start, int end, String token) {

    public TokenSpan {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid span: " + start + ".." + end);
        }
    }
}
