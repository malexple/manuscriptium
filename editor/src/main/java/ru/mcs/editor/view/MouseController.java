package ru.mcs.editor.view;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/** Hit-test, drag selection, double-click word и triple-click physical line. */
public final class MouseController extends MouseAdapter {

    private final EditorCanvas canvas;
    private final InputController input;
    private boolean dragging;
    private boolean resizingImage;

    MouseController(EditorCanvas canvas, InputController input) {
        this.canvas = canvas;
        this.input = input;
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (event.isPopupTrigger()) {
            canvas.showContextMenu(event);
            dragging = false;
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(event) || canvas.document() == null) {
            return;
        }
        canvas.requestFocusInWindow();
        canvas.clearComposition();
        if (event.getClickCount() == 1 && canvas.beginImageResize(event.getPoint())) {
            resizingImage = true;
            dragging = false;
            event.consume();
            return;
        }
        if (event.getClickCount() == 1 && canvas.toggleTaskAt(event.getPoint())) {
            event.consume();
            return;
        }
        if (event.getClickCount() == 1 && (event.isControlDown() || event.isMetaDown())
                && canvas.openLinkAt(event.getPoint())) {
            event.consume();
            return;
        }
        int offset = canvas.hitTest(event.getPoint());
        if (event.getClickCount() >= 3) {
            input.selectLine(offset);
            dragging = false;
        } else if (event.getClickCount() == 2) {
            input.selectWord(offset);
            dragging = false;
        } else {
            canvas.setCaretState(offset, event.isShiftDown() ? canvas.selectionAnchor() : offset);
            canvas.repaint();
            dragging = true;
        }
        event.consume();
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        if (resizingImage) {
            event.consume();
            return;
        }
        if (!dragging || canvas.document() == null) {
            return;
        }
        int offset = canvas.hitTest(event.getPoint());
        canvas.setCaretState(offset, canvas.selectionAnchor());
        canvas.ensureCaretVisible();
        canvas.repaint();
        event.consume();
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (resizingImage) {
            resizingImage = false;
            canvas.finishImageResize(event.getPoint());
            event.consume();
            return;
        }
        if (event.isPopupTrigger()) {
            canvas.showContextMenu(event);
        }
        dragging = false;
    }
}
