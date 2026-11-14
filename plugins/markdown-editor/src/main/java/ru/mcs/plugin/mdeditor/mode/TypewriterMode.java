package ru.mcs.plugin.mdeditor.mode;

import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JViewport;
import ru.mcs.plugin.mdeditor.view.EditorCanvas;

/** Удерживает строку каретки по вертикальному центру viewport, не меняя модель документа. */
public final class TypewriterMode {

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void centerCaret(EditorCanvas canvas) {
        if (!enabled || canvas.document() == null || !(canvas.getParent() instanceof JViewport viewport)) {
            return;
        }
        int height = viewport.getHeight();
        if (height <= 0) {
            return;
        }
        Rectangle caret = canvas.caretRect(canvas.caretOffset());
        Point current = viewport.getViewPosition();
        int desired = caret.y + topPadding(canvas) + caret.height / 2 - height / 2;
        int maximum = Math.max(0, canvas.getPreferredSize().height - height);
        int centered = Math.max(0, Math.min(maximum, desired));
        if (current.y != centered) {
            viewport.setViewPosition(new Point(current.x, centered));
        }
    }

    /** Виртуальное место перед первой строкой; координаты внутри layout не меняются. */
    public int topPadding(EditorCanvas canvas) {
        return padding(canvas);
    }

    /** Виртуальное место после последней строки; содержимое layout не растягивается. */
    public int bottomPadding(EditorCanvas canvas) {
        return padding(canvas);
    }

    private int padding(EditorCanvas canvas) {
        return enabled && canvas.getParent() instanceof JViewport viewport ? viewport.getHeight() / 2 : 0;
    }

    public void restoreViewport(EditorCanvas canvas, int removedTopPadding) {
        if (!(canvas.getParent() instanceof JViewport viewport)) {
            return;
        }
        Point current = viewport.getViewPosition();
        int maximum = Math.max(0, canvas.getPreferredSize().height - viewport.getHeight());
        int restored = Math.max(0, Math.min(maximum, current.y - removedTopPadding));
        if (current.y != restored) {
            viewport.setViewPosition(new Point(current.x, restored));
        }
    }
}
