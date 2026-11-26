package ru.mcs.spi.swing.editor.keymap;

import java.util.List;
import java.util.Objects;

/** Область, в которой действуют свои сочетания (например, визуальный режим или исходный текст). */
public record ShortcutScope(String id, String displayName, List<ShortcutBinding> bindings) {

    public ShortcutScope {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        bindings = List.copyOf(bindings);
    }
}
