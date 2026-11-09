package ru.mcs.core.toolwindow;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Узкая полоса с кнопками панелей одной стороны. Скрыта, если панелей нет. */
final class Stripe extends JPanel {

    private final ToolWindowAnchor anchor;

    Stripe(ToolWindowAnchor anchor) {
        this.anchor = anchor;
        setLayout(new BoxLayout(this, anchor == ToolWindowAnchor.BOTTOM ? BoxLayout.X_AXIS : BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(2, 2, 2, 2));
    }

    void rebuild(ToolWindowManagerImpl manager) {
        removeAll();
        int rotation = switch (anchor) {
            case LEFT -> -1;
            case RIGHT -> 1;
            case BOTTOM -> 0;
        };
        int count = 0;
        for (ToolWindowImpl tw : manager.windows()) {
            if (tw.getAnchor() == anchor) {
                add(new StripeButton(tw, rotation));
                count++;
            }
        }
        add(Box.createGlue());
        setVisible(count > 0);
        revalidate();
        repaint();
    }
}
