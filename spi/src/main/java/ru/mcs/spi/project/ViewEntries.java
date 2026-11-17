package ru.mcs.spi.project;

import java.util.List;

/** Общая навигация по индексному пути узла view для core и языковых проекций. */
public final class ViewEntries {

    private ViewEntries() {
    }

    public static ViewEntry entryAt(List<ViewEntry> roots, List<Integer> path) {
        List<ViewEntry> current = roots;
        ViewEntry selected = null;
        for (int index : path) {
            if (index < 0 || index >= current.size()) {
                return null;
            }
            selected = current.get(index);
            current = selected.children();
        }
        return selected;
    }
}
