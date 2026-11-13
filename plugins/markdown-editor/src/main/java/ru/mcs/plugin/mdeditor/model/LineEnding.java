package ru.mcs.plugin.mdeditor.model;

/** Перевод строки, используемый при кодировании документа обратно в файл. */
public enum LineEnding {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r");

    private final String sequence;

    LineEnding(String sequence) {
        this.sequence = sequence;
    }

    public String sequence() {
        return sequence;
    }
}
