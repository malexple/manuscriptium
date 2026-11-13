package ru.mcs.plugin.mdeditor.model;

import java.util.List;

@FunctionalInterface
public interface DocumentListener {

    void changed(DocumentChange change);

    /** Пакет уже применённых изменений; по умолчанию сохраняется прежнее поэлементное поведение. */
    default void changed(List<DocumentChange> changes) {
        for (DocumentChange change : changes) {
            changed(change);
        }
    }
}
