package ru.mcs.editor.action;

import ru.mcs.metadata.MetadataProvider;

/** Описание команд и keymap редактора; их реализации остаются в editor. */
public final class EditorMetadataProvider implements MetadataProvider {

    @Override
    public String id() {
        return "editor";
    }
}
