package ru.mcs.plugin.mdeditor.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.UIManager;

/** Собственный лёгкий холст редактора. E0 отвечает только за жизненный цикл и отрисовку состояния. */
public final class EditorCanvas extends JComponent {

    private String status = "";

    public EditorCanvas() {
        setName("markdown.editor.canvas");
        setFocusable(true);
        setOpaque(true);
        setPreferredSize(new Dimension(800, 600));
        Color background = UIManager.getColor("EditorPane.background");
        setBackground(background == null ? Color.WHITE : background);
        Color foreground = UIManager.getColor("Label.foreground");
        setForeground(foreground == null ? Color.DARK_GRAY : foreground);
        Font font = UIManager.getFont("Label.font");
        setFont(font == null ? new Font(Font.DIALOG, Font.PLAIN, 15) : font);
    }

    public void setStatus(String text) {
        status = text == null ? "" : text;
        repaint();
    }

    public String getStatus() {
        return status;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(Font.PLAIN, 15.0f));
            g.setColor(foreground());
            FontMetrics metrics = g.getFontMetrics();
            String text = fit(status, metrics, Math.max(0, getWidth() - 40));
            int x = Math.max(20, (getWidth() - metrics.stringWidth(text)) / 2);
            int y = Math.max(metrics.getAscent() + 20, (getHeight() + metrics.getAscent()) / 2);
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }
    }

    private Color foreground() {
        Color disabled = UIManager.getColor("Label.disabledForeground");
        return disabled == null ? getForeground() : disabled;
    }

    private static String fit(String text, FontMetrics metrics, int width) {
        if (text.isEmpty() || metrics.stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
