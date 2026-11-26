package ru.mcs.spi.swing.editor.keymap;

import java.util.Objects;

/** Действие области и его сочетание по умолчанию в текстовой записи {@link Shortcuts} (пусто — без сочетания). */
public record ShortcutBinding(String actionId, String name, String defaultShortcut) {

    public ShortcutBinding {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(name, "name");
        defaultShortcut = defaultShortcut == null ? "" : defaultShortcut.trim();
    }
}
