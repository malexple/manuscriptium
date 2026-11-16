package ru.mcs.plugin.mdeditor.action;

import java.awt.Rectangle;
import javax.swing.JComponent;
import ru.mcs.plugin.mdeditor.model.DocumentModel;

/** Минимальный живой контекст редактора для keymap и viewport-режимов. */
public interface EditorContext {

    JComponent component();

    DocumentModel document();

    int caretOffset();

    Rectangle caretRect(int offset);
}
