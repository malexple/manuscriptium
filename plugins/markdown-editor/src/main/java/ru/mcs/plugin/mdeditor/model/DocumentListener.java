package ru.mcs.plugin.mdeditor.model;

@FunctionalInterface
public interface DocumentListener {

    void changed(DocumentChange change);
}
