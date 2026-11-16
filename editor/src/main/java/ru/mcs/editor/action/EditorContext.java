package ru.mcs.editor.action;

import java.awt.Rectangle;
import javax.swing.JComponent;
import ru.mcs.editor.model.DocumentModel;

/** Минимальный живой контекст редактора для keymap и viewport-режимов. */
public interface EditorContext {

    JComponent component();

    DocumentModel document();

    int caretOffset();

    Rectangle caretRect(int offset);
}
