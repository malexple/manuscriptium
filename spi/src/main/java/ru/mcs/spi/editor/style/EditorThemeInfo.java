package ru.mcs.spi.editor.style;

import java.util.Objects;

/** Описание встроенной или файловой Typora-темы. */
public record EditorThemeInfo(String id, String name, boolean dark, boolean builtIn) {

    public EditorThemeInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
