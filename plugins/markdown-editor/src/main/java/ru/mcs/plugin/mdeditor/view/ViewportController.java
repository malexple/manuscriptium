package ru.mcs.plugin.mdeditor.view;

import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Wheel scrolling с небольшой инерцией; все координаты остаются в logical Swing units при HiDPI. */
public final class ViewportController {

    private static final int FRAME_MILLIS = 16;
    private static final double FRICTION = 0.82;

    private final EditorCanvas canvas;
    private final Timer timer;
    private double velocity;

    ViewportController(EditorCanvas canvas) {
        this.canvas = canvas;
        timer = new Timer(FRAME_MILLIS, event -> advanceInertia());
        timer.setRepeats(true);
        canvas.addMouseWheelListener(this::mouseWheelMoved);
    }

    void dispose() {
        timer.stop();
    }

    private void mouseWheelMoved(MouseWheelEvent event) {
        if (canvas.document() == null) {
            return;
        }
        double amount = event.getPreciseWheelRotation() * event.getScrollAmount()
                * canvas.textLayout().lineHeight();
        if (event.isShiftDown()) {
            amount *= 2;
        }
        scrollBy((int) Math.round(amount));
        velocity = amount * 0.45;
        timer.restart();
        event.consume();
    }

    private void advanceInertia() {
        if (Math.abs(velocity) < 0.5) {
            velocity = 0;
            timer.stop();
            return;
        }
        scrollBy((int) Math.round(velocity));
        velocity *= FRICTION;
    }

    private void scrollBy(int deltaY) {
        if (deltaY == 0) {
            return;
        }
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, canvas);
        if (viewport != null) {
            Point position = viewport.getViewPosition();
            int maximum = Math.max(0, viewport.getViewSize().height - viewport.getExtentSize().height);
            position.y = Math.max(0, Math.min(maximum, position.y + deltaY));
            viewport.setViewPosition(position);
        } else {
            java.awt.Rectangle visible = canvas.getVisibleRect();
            visible.y = Math.max(0, visible.y + deltaY);
            canvas.scrollRectToVisible(visible);
        }
    }
}
