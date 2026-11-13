package ru.mcs.plugin.mdeditor.model;

/** Изменение текста; версия строго возрастает, включая undo и redo. */
public record DocumentChange(int start, int oldLen, int newLen, long version) {

    public DocumentChange {
        if (start < 0 || oldLen < 0 || newLen < 0 || version <= 0) {
            throw new IllegalArgumentException("Invalid document change");
        }
    }
}
