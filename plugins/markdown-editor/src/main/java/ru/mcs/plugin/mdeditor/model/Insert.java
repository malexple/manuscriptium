package ru.mcs.plugin.mdeditor.model;

import java.util.Objects;

public record Insert(int offset, String text) implements EditCommand {

    public Insert {
        if (offset < 0) {
            throw new IllegalArgumentException("Insert offset must be non-negative: " + offset);
        }
        Objects.requireNonNull(text, "text");
    }
}
