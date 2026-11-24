package ru.mcs.markdown.editor.action;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.Icon;
import javax.swing.UIManager;

/** Маленькие векторные значки быстрых кнопок ПКМ; цвет берётся у кнопки, поэтому работают в любой теме. */
final class QuickFormatIcon implements Icon {

    enum Kind { BOLD, ITALIC, ASTERISK, LINK, QUOTE, ORDERED, BULLET, TASK }

    private static final int GRID = 16;
    private static final int SIZE = 14;

    private final Kind kind;

    QuickFormatIcon(Kind kind) {
        this.kind = kind;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.translate(x, y);
            g.scale((double) SIZE / GRID, (double) SIZE / GRID);
            g.setColor(color(component));
            switch (kind) {
                case BOLD -> glyph(g, "B", new Font(Font.SERIF, Font.BOLD, 16));
                case ITALIC -> glyph(g, "I", new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 16));
                case ASTERISK -> asterisk(g);
                case LINK -> link(g);
                case QUOTE -> quote(g);
                case ORDERED -> orderedList(g);
                case BULLET -> bulletList(g);
                case TASK -> taskList(g);
                default -> throw new IllegalStateException("Unknown icon kind: " + kind);
            }
        } finally {
            g.dispose();
        }
    }

    private static Color color(Component component) {
        Color color = null;
        if (component != null) {
            color = component.isEnabled() ? component.getForeground() : UIManager.getColor("Label.disabledForeground");
        }
        if (color == null) {
            color = UIManager.getColor("Label.foreground");
        }
        return color == null ? Color.DARK_GRAY : color;
    }

    private static void glyph(Graphics2D g, String text, Font font) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int x = (GRID - metrics.stringWidth(text)) / 2;
        int y = (GRID - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(text, x, y);
    }

    private static void asterisk(Graphics2D g) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double radius = 6;
        for (int degrees : new int[]{90, 30, 150}) {
            double angle = Math.toRadians(degrees);
            double dx = Math.cos(angle) * radius;
            double dy = Math.sin(angle) * radius;
            g.draw(new java.awt.geom.Line2D.Double(GRID / 2.0 - dx, GRID / 2.0 - dy, GRID / 2.0 + dx, GRID / 2.0 + dy));
        }
    }

    private static void link(Graphics2D g) {
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.rotate(-Math.PI / 4, GRID / 2.0, GRID / 2.0);
        g.drawRoundRect(1, 5, 8, 6, 6, 6);
        g.drawRoundRect(7, 5, 8, 6, 6, 6);
    }

    private static void quote(Graphics2D g) {
        g.setFont(new Font(Font.SERIF, Font.BOLD, 26));
        FontMetrics metrics = g.getFontMetrics();
        String text = "\u201C";
        g.drawString(text, (GRID - metrics.stringWidth(text)) / 2, 17);
    }

    private static void orderedList(Graphics2D g) {
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 6));
        for (int row = 0; row < 3; row++) {
            int y = 4 + row * 4;
            g.drawString(Integer.toString(row + 1), 1, y + 2);
            g.drawLine(7, y, 15, y);
        }
    }

    private static void bulletList(Graphics2D g) {
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int row = 0; row < 3; row++) {
            int y = 4 + row * 4;
            g.fillOval(1, y - 1, 3, 3);
            g.drawLine(7, y, 15, y);
        }
    }

    private static void taskList(Graphics2D g) {
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawRoundRect(2, 2, 12, 12, 3, 3);
        Path2D check = new Path2D.Double();
        check.moveTo(5, 8);
        check.lineTo(7, 10.5);
        check.lineTo(11, 5.5);
        g.draw(check);
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
