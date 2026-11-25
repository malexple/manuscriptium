package ru.mcs.editor.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Колонка номеров строк для {@link EditorCanvas}. Подключается как row header окна прокрутки:
 * {@code scrollPane.setRowHeaderView(new LineNumberGutter(canvas))}. Клик по номеру выделяет строку,
 * перетаскивание — несколько строк. Вызовите {@link #dispose()}, когда канва больше не нужна.
 */
public final class LineNumberGutter extends JComponent {

    private static final int PADDING_LEFT = 8;
    private static final int PADDING_RIGHT = 10;
    private static final int MIN_DIGITS = 3;

    private final EditorCanvas canvas;
    private final Runnable listener = this::canvasChanged;
    private Dimension lastSize = new Dimension();
    private int dragFromY = -1;

    public LineNumberGutter(EditorCanvas canvas) {
        this.canvas = canvas;
        setName("editor.lineNumbers");
        setOpaque(true);
        canvas.addViewListener(listener);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    dragFromY = event.getY();
                    canvas.selectLines(dragFromY, dragFromY);
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragFromY >= 0) {
                    canvas.selectLines(dragFromY, event.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragFromY = -1;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void dispose() {
        canvas.removeViewListener(listener);
    }

    private void canvasChanged() {
        Dimension size = getPreferredSize();
        if (!size.equals(lastSize)) {
            lastSize = size;
            revalidate();
        }
        repaint();
    }

    private int digits() {
        return Math.max(MIN_DIGITS, Integer.toString(canvas.lineCount()).length());
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(canvas.getFont());
        int width = PADDING_LEFT + digits() * metrics.charWidth('0') + PADDING_RIGHT + 1;
        int height = Math.max(canvas.getHeight(), canvas.getPreferredSize().height);
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Rectangle clip = g.getClipBounds() == null ? new Rectangle(0, 0, getWidth(), getHeight())
                    : g.getClipBounds();
            g.setColor(canvas.getBackground());
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            Color foreground = canvas.getForeground();
            g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 50));
            g.fillRect(getWidth() - 1, clip.y, 1, clip.height);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(canvas.getFont());
            FontMetrics metrics = g.getFontMetrics();
            Color muted = new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 110);
            int current = canvas.currentLineNumber();
            for (EditorCanvas.LineMark mark : canvas.visibleLineMarks(clip)) {
                String text = Integer.toString(mark.number());
                g.setColor(mark.number() == current ? foreground : muted);
                g.drawString(text, getWidth() - PADDING_RIGHT - metrics.stringWidth(text), mark.baseline());
            }
        } finally {
            g.dispose();
        }
    }
}
