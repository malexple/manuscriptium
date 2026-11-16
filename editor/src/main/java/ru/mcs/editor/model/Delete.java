package ru.mcs.editor.model;

public record Delete(Range range) implements EditCommand {

    public Delete {
        if (range == null) {
            throw new NullPointerException("range");
        }
    }

    public static Delete of(int start, int end) {
        return new Delete(new Range(start, end));
    }
}
