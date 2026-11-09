package ru.mcs.spi.editor;

import java.nio.file.Path;

/** События области редактора (тема {@link EditorTopics#EDITOR}). */
public interface FileEditorListener {

    default void fileOpened(Path file) {
    }

    default void fileClosed(Path file) {
    }

    /** @param file активный файл или null, если открытых вкладок нет */
    default void activeFileChanged(Path file) {
    }
}
