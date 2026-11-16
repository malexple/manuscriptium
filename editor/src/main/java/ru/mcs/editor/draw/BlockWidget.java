package ru.mcs.editor.draw;

import java.awt.Rectangle;
import javax.swing.JComponent;

/** Lightweight-виджет внутри layout: не создаёт Swing child component и второй документ. */
public interface BlockWidget {

    int height(int width);

    void paint(DrawSink sink, int x, int y, int width);

    Rectangle bounds();

    /** Точный source-span виджета; полезен для hit-test и точечных Markdown-операций. */
    int sourceStart();

    int sourceEnd();

    /** Интерактивная часть трансклюзии; обычные виджеты остаются полностью lightweight. */
    default JComponent component() {
        return null;
    }
}
