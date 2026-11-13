package ru.mcs.plugin.mdeditor.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.UIManager;
import ru.mcs.plugin.mdeditor.model.Range;

/** Рисует selection по visual lines, включая частично выделенные wrapped-строки. */
final class SelectionPainter {

    private final EditorCanvas canvas;

    SelectionPainter(EditorCanvas canvas) {
        this.canvas = canvas;
    }

    void paint(Graphics2D graphics, Iterable<TextLayoutEngine.VisualLine> visibleLines) {
        Range selection = canvas.selectionRange();
        if (selection.length() == 0) {
            return;
        }
        Color color = UIManager.getColor("TextPane.selectionBackground");
        graphics.setColor(color == null ? new Color(0xB3D4FC) : color);
        for (TextLayoutEngine.VisualLine line : visibleLines) {
            int start = Math.max(selection.start(), line.start());
            int end = Math.min(selection.end(), line.end());
            if (start >= end) {
                continue;
            }
            int x1 = TextLayoutEngine.HORIZONTAL_PADDING + Math.round(canvas.textLayout().caretX(line, start));
            int x2 = TextLayoutEngine.HORIZONTAL_PADDING + Math.round(canvas.textLayout().caretX(line, end));
            Rectangle bounds = new Rectangle(Math.min(x1, x2), line.y(), Math.max(1, Math.abs(x2 - x1)),
                    canvas.textLayout().lineHeight());
            graphics.fill(bounds);
        }
    }
}
