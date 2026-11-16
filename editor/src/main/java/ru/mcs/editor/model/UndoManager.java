package ru.mcs.editor.model;

public interface UndoManager {

    boolean canUndo();

    boolean canRedo();

    void undo();

    void redo();

    void clear();
}
