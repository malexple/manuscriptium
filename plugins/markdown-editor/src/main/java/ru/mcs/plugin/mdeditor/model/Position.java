package ru.mcs.plugin.mdeditor.model;

/** Нулевая строка и нулевая UTF-16 колонка в документе. */
public record Position(int line, int column) {

    public Position {
        if (line < 0 || column < 0) {
            throw new IllegalArgumentException("Position must be non-negative: " + line + ":" + column);
        }
    }
}
