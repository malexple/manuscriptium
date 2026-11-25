package ru.mcs.editor.view;

/** Участок текста документа [start, end) с именем токена подсветки. */
public record HighlightSpan(int start, int end, String token) {

    public HighlightSpan {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid span: " + start + ".." + end);
        }
    }
}
