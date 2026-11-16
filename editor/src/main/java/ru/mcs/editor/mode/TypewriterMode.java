package ru.mcs.editor.mode;

import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JViewport;
import ru.mcs.editor.action.EditorContext;

/** Удерживает строку каретки по вертикальному центру viewport, не меняя модель документа. */
public final class TypewriterMode {

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void centerCaret(EditorContext context) {
        JComponent component = context.component();
        if (!enabled || context.document() == null || !(component.getParent() instanceof JViewport viewport)) {
            return;
        }
        int height = viewport.getHeight();
        if (height <= 0) {
            return;
        }
        Rectangle caret = context.caretRect(context.caretOffset());
        Point current = viewport.getViewPosition();
        int desired = caret.y + topPadding(context) + caret.height / 2 - height / 2;
        int maximum = Math.max(0, component.getPreferredSize().height - height);
        int centered = Math.max(0, Math.min(maximum, desired));
        if (current.y != centered) {
            viewport.setViewPosition(new Point(current.x, centered));
        }
    }

    /** Виртуальное место перед первой строкой; координаты внутри layout не меняются. */
    public int topPadding(EditorContext context) {
        return padding(context);
    }

    /** Виртуальное место после последней строки; содержимое layout не растягивается. */
    public int bottomPadding(EditorContext context) {
        return padding(context);
    }

    private int padding(EditorContext context) {
        return enabled && context.component().getParent() instanceof JViewport viewport ? viewport.getHeight() / 2 : 0;
    }

    public void restoreViewport(EditorContext context, int removedTopPadding) {
        JComponent component = context.component();
        if (!(component.getParent() instanceof JViewport viewport)) {
            return;
        }
        Point current = viewport.getViewPosition();
        int maximum = Math.max(0, component.getPreferredSize().height - viewport.getHeight());
        int restored = Math.max(0, Math.min(maximum, current.y - removedTopPadding));
        if (current.y != restored) {
            viewport.setViewPosition(new Point(current.x, restored));
        }
    }
}
