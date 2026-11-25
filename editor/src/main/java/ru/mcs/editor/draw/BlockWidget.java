package ru.mcs.editor.draw;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JComponent;

/** Lightweight-виджет внутри layout: не создаёт Swing child component и второй документ. */
public interface BlockWidget {

    int height(int width);

    void paint(DrawSink sink, int x, int y, int width);

    Rectangle bounds();

    /** Точный source-span виджета; полезен для hit-test и точечных операций с документом. */
    int sourceStart();

    int sourceEnd();

    /** Каретка внутри виджета в координатах canvas; {@code null}, если виджет не редактируется по ячейкам. */
    default Rectangle caretRect(int offset, int x, int y, int width) {
        return null;
    }

    /** Source-offset под точкой клика; {@code -1}, если виджет не умеет принимать каретку. */
    default int hitOffset(Point point, int x, int y, int width) {
        return -1;
    }

    default List<Rectangle> selectionRects(int start, int end, int x, int y, int width) {
        return List.of();
    }

    /** Интерактивная часть трансклюзии; обычные виджеты остаются полностью lightweight. */
    default JComponent component() {
        return null;
    }
}
