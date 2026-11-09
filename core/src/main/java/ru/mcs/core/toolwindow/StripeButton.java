package ru.mcs.core.toolwindow;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/** Кнопка на stripe. Для боковых сторон текст поворачивается на 90°, как в IntelliJ IDEA. */
final class StripeButton extends JToggleButton {

    private final int rotation;

    /** @param rotation -1 — слева (текст снизу вверх), +1 — справа (сверху вниз), 0 — без поворота */
    StripeButton(ToolWindowImpl tw, int rotation) {
        super(tw.getTitle());
        this.rotation = rotation;
        setFocusable(false);
        setRolloverEnabled(true);
        setSelected(tw.isVisible());
        setToolTipText(tw.getTitle());
        putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        addActionListener(e -> tw.toggle());
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return rotation == 0 ? d : new Dimension(d.height, d.width);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (rotation == 0) {
            super.paintComponent(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (isSelected() || getModel().isRollover()) {
                g2.setColor(highlight(isSelected()));
                g2.fillRoundRect(1, 1, w - 2, h - 2, 8, 8);
            }
            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();
            if (rotation < 0) {
                g2.translate(0, h);
                g2.rotate(-Math.PI / 2);
            } else {
                g2.translate(w, 0);
                g2.rotate(Math.PI / 2);
            }
            int textWidth = fm.stringWidth(getText());
            g2.drawString(getText(), (h - textWidth) / 2, (w + fm.getAscent() - fm.getDescent()) / 2);
        } finally {
            g2.dispose();
        }
    }

    private static Color highlight(boolean selected) {
        Color base = UIManager.getColor("Component.accentColor");
        if (base == null) {
            base = new Color(0x3592C4);
        }
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), selected ? 70 : 35);
    }
}
