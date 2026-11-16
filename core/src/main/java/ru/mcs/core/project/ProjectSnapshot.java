package ru.mcs.core.project;

import java.util.List;

/** Атомарный снимок для UI: физические заметки, виртуальные деревья и неблокирующие ошибки views. */
record ProjectSnapshot(List<ProjectNote> notes, List<ViewDefinition> views, List<String> viewErrors) {

    ProjectSnapshot {
        notes = List.copyOf(notes);
        views = List.copyOf(views);
        viewErrors = List.copyOf(viewErrors);
    }

    static ProjectSnapshot empty() {
        return new ProjectSnapshot(List.of(), List.of(), List.of());
    }
}
