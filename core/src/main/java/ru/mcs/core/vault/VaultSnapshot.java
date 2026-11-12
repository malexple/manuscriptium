package ru.mcs.core.vault;

import java.util.List;

/** Атомарный снимок для UI: физические заметки, виртуальные деревья и неблокирующие ошибки views. */
record VaultSnapshot(List<VaultNote> notes, List<ViewDefinition> views, List<String> viewErrors) {

    VaultSnapshot {
        notes = List.copyOf(notes);
        views = List.copyOf(views);
        viewErrors = List.copyOf(viewErrors);
    }

    static VaultSnapshot empty() {
        return new VaultSnapshot(List.of(), List.of(), List.of());
    }
}
