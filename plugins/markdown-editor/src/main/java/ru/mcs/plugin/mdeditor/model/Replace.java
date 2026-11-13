package ru.mcs.plugin.mdeditor.model;

import java.util.Objects;

public record Replace(Range range, String text) implements EditCommand {

    public Replace {
        if (range == null) {
            throw new NullPointerException("range");
        }
        Objects.requireNonNull(text, "text");
    }

    public static Replace of(int start, int end, String text) {
        return new Replace(new Range(start, end), text);
    }
}
