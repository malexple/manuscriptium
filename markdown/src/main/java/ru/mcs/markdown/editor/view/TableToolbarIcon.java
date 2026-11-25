package ru.mcs.markdown.editor.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;
import javax.swing.Icon;

/** Векторные значки панели таблицы: цвет берётся у редактора, чтобы подходить любой теме. */
final class TableToolbarIcon implements Icon {

    enum Kind {
        GRID, ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT, SORT_ASC, SORT_DESC, MORE, DELETE
    }

    private static final int SIZE = 14;
    private static final double[] LINE_WIDTHS = {12, 8, 12, 8};

    private final Kind kind;
    private final Supplier<Color> color;

    TableToolbarIcon(Kind kind, Supplier<Color> color) {
        this.kind = kind;
        this.color = color;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = color.get() == null ? Color.DARK_GRAY : color.get();
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 210));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case GRID -> {
                    for (double[] cell : new double[][]{{1, 1}, {8, 1}, {1, 8}, {8, 8}}) {
                        g.draw(new RoundRectangle2D.Double(cell[0] + 0.5, cell[1] + 0.5, 4, 4, 1, 1));
                    }
                }
                case ALIGN_LEFT -> lines(g, 0);
                case ALIGN_CENTER -> lines(g, 1);
                case ALIGN_RIGHT -> lines(g, 2);
                case SORT_ASC -> arrow(g, true);
                case SORT_DESC -> arrow(g, false);
                case MORE -> {
                    for (double cy : new double[]{3, 7, 11}) {
                        g.fill(new Ellipse2D.Double(5.7, cy - 1.3, 2.6, 2.6));
                    }
                }
                case DELETE -> {
                    g.draw(new Line2D.Double(2, 3.5, 12, 3.5));
                    Path2D handle = new Path2D.Double();
                    handle.moveTo(5, 3.5);
                    handle.lineTo(5, 1.5);
                    handle.lineTo(9, 1.5);
                    handle.lineTo(9, 3.5);
                    g.draw(handle);
                    Path2D body = new Path2D.Double();
                    body.moveTo(3.5, 3.5);
                    body.lineTo(4.5, 12.5);
                    body.lineTo(9.5, 12.5);
                    body.lineTo(10.5, 3.5);
                    g.draw(body);
                    g.draw(new Line2D.Double(6, 6, 6, 10.5));
                    g.draw(new Line2D.Double(8, 6, 8, 10.5));
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** Стрелка вверх (по возрастанию) или вниз (по убыванию). */
    private static void arrow(Graphics2D g, boolean up) {
        double tip = up ? 1.5 : SIZE - 1.5;
        double tail = up ? SIZE - 1.5 : 1.5;
        double wing = up ? tip + 4 : tip - 4;
        g.draw(new Line2D.Double(7, tail, 7, tip));
        Path2D head = new Path2D.Double();
        head.moveTo(3.5, wing);
        head.lineTo(7, tip);
        head.lineTo(10.5, wing);
        g.draw(head);
    }

    private static void lines(Graphics2D g, int mode) {
        for (int index = 0; index < LINE_WIDTHS.length; index++) {
            double width = LINE_WIDTHS[index];
            double left = switch (mode) {
                case 0 -> 1;
                case 1 -> (SIZE - width) / 2;
                default -> SIZE - 1 - width;
            };
            double y = 2.5 + index * 3;
            g.draw(new Line2D.Double(left, y, left + width, y));
        }
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
