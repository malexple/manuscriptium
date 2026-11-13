package ru.mcs.plugin.mdeditor.model;

public interface UndoManager {

    boolean canUndo();

    boolean canRedo();

    void undo();

    void redo();

    void clear();
}
