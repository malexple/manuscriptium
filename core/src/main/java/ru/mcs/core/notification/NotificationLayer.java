package ru.mcs.core.notification;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import ru.mcs.spi.ui.NotificationType;

/**
 * Прозрачный слой поверх окна с баллонами в правом нижнем углу. Пропускает мышь везде, кроме самих баллонов.
 */
public final class NotificationLayer extends JPanel {

    private static final int MAX_VISIBLE = 4;
    private static final int WIDTH = 320;
    private static final int MARGIN = 12;
    private static final int GAP = 8;
    private static final int BOTTOM_OFFSET = 36;
    private static final int AUTO_HIDE_MS = 6000;

    public static NotificationLayer install(JFrame frame, NotificationManagerImpl manager) {
        NotificationLayer layer = new NotificationLayer();
        JLayeredPane pane = frame.getLayeredPane();
        pane.add(layer, JLayeredPane.POPUP_LAYER);
        layer.setBounds(0, 0, pane.getWidth(), pane.getHeight());
        pane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layer.setBounds(0, 0, pane.getWidth(), pane.getHeight());
                layer.revalidate();
            }
        });
        manager.addListener(n -> SwingUtilities.invokeLater(() -> layer.showNotice(n)));
        return layer;
    }

    private NotificationLayer() {
        super(null);
        setOpaque(false);
    }

    @Override
    public boolean contains(int x, int y) {
        for (Component c : getComponents()) {
            if (c.getBounds().contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void doLayout() {
        int y = getHeight() - BOTTOM_OFFSET;
        Component[] children = getComponents();
        for (int i = children.length - 1; i >= 0; i--) {
            Dimension d = children[i].getPreferredSize();
            y -= d.height;
            children[i].setBounds(getWidth() - WIDTH - MARGIN, y, WIDTH, d.height);
            y -= GAP;
        }
    }

    private void showNotice(NotificationManagerImpl.Notice notice) {
        JPanel balloon = createBalloon(notice);
        add(balloon);
        while (getComponentCount() > MAX_VISIBLE) {
            remove(0);
        }
        revalidate();
        repaint();
        if (notice.type() != NotificationType.ERROR) {
            Timer timer = new Timer(AUTO_HIDE_MS, e -> dismiss(balloon));
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void dismiss(JPanel balloon) {
        if (balloon.getParent() == this) {
            remove(balloon);
            revalidate();
            repaint();
        }
    }

    private JPanel createBalloon(NotificationManagerImpl.Notice notice) {
        Color accent = switch (notice.type()) {
            case INFO -> new Color(0x3592C4);
            case WARNING -> new Color(0xE0A030);
            case ERROR -> new Color(0xD9534F);
        };
        Color border = UIManager.getColor("Component.borderColor");
        JPanel balloon = new JPanel(new BorderLayout());
        Color background = UIManager.getColor("Panel.background");
        balloon.setBackground(background != null ? background : Color.WHITE);
        balloon.setOpaque(true);
        balloon.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border != null ? border : Color.GRAY),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10))));
        JLabel text = new JLabel("<html><body style='width:240px'><b>" + escape(notice.title()) + "</b><br>"
                + escape(notice.content()) + "</body></html>");
        balloon.add(text, BorderLayout.CENTER);
        balloon.setToolTipText("Click to dismiss");
        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dismiss(balloon);
            }
        };
        balloon.addMouseListener(click);
        text.addMouseListener(click);
        return balloon;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
