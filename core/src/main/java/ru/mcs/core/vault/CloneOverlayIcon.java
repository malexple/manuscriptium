package ru.mcs.core.vault;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.UIManager;

/** Маленький значок двух ссылок поверх обычной иконки Markdown. */
final class CloneOverlayIcon implements Icon {

    private final Icon base;

    CloneOverlayIcon(Icon base) {
        this.base = base;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        base.paintIcon(component, graphics, x, y);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color foreground = component == null ? UIManager.getColor("Label.foreground") : component.getForeground();
            Color background = component == null ? UIManager.getColor("Tree.background") : component.getBackground();
            if (foreground == null) {
                foreground = Color.GRAY;
            }
            if (background == null) {
                background = Color.WHITE;
            }
            int left = x + getIconWidth() - 8;
            int top = y + getIconHeight() - 7;
            g.setColor(background);
            g.fillRect(left - 2, top - 2, 5, 6);
            g.fillRect(left, top, 5, 6);
            g.setColor(foreground);
            g.drawRect(left - 2, top - 2, 4, 5);
            g.drawRect(left, top, 4, 5);
        } finally {
            g.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return base.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return base.getIconHeight();
    }
}
