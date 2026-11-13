package ru.mcs.plugin.mdeditor.view;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.Timer;

/** Мигающая каретка; Timer останавливается при потере фокуса и dispose. */
final class CaretPainter {

    private final EditorCanvas canvas;
    private final Timer timer;
    private boolean focused;
    private boolean visible = true;

    CaretPainter(EditorCanvas canvas) {
        this.canvas = canvas;
        timer = new Timer(500, event -> {
            visible = !visible;
            Rectangle caret = canvas.document() == null ? null : canvas.caretRect(canvas.caretOffset());
            if (caret != null) {
                canvas.repaint(caret.x - 2, caret.y - 1, caret.width + 4, caret.height + 2);
            }
        });
        timer.setRepeats(true);
    }

    void focusChanged(boolean focused) {
        this.focused = focused;
        visible = true;
        if (focused && canvas.document() != null) {
            timer.start();
        } else {
            timer.stop();
        }
        canvas.repaint();
    }

    void documentChanged() {
        visible = true;
        if (focused) {
            timer.restart();
        }
    }

    void paint(Graphics2D graphics) {
        if (!focused || !visible || canvas.document() == null || canvas.selectionRange().length() != 0) {
            return;
        }
        Rectangle caret = canvas.caretRect(canvas.caretOffset());
        graphics.setColor(canvas.getForeground());
        graphics.fillRect(caret.x, caret.y + 1, Math.max(1, caret.width), Math.max(1, caret.height - 2));
    }

    void dispose() {
        timer.stop();
    }
}
