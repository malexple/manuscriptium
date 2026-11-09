package ru.mcs.core.shell;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Временная оболочка Фазы 0: три зоны, меню Tools, статус. Заменяется в Фазе 1. */
public final class ShellFrame extends JFrame implements UiHost {

    private final JTabbedPane left = zone(280, 0);
    private final JTabbedPane right = zone(260, 0);
    private final JTabbedPane bottom = zone(0, 200);
    private final JMenu tools = new JMenu("Tools");
    private final JLabel status = new JLabel(" ");

    public ShellFrame(String title) {
        super(title);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JMenuBar bar = new JMenuBar();
        bar.add(tools);
        setJMenuBar(bar);

        add(new JLabel("Editor area (Phase 1)", SwingConstants.CENTER), BorderLayout.CENTER);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        JComponent south = new javax.swing.JPanel(new BorderLayout());
        south.add(bottom, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private static JTabbedPane zone(int w, int h) {
        JTabbedPane pane = new JTabbedPane();
        pane.setPreferredSize(new Dimension(w, h));
        pane.setVisible(false);
        return pane;
    }

    public void setStatus(String text) {
        onEdt(() -> status.setText(" " + text));
    }

    @Override
    public void addAction(String pluginId, String id, String text, Runnable handler) {
        onEdt(() -> {
            JMenuItem item = new JMenuItem(new AbstractAction(text) {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    handler.run();
                }
            });
            item.setName(id);
            tools.add(item);
        });
    }

    @Override
    public void addToolWindow(String pluginId, String id, String title, ToolWindowAnchor anchor,
                              Supplier<JComponent> factory) {
        onEdt(() -> {
            JTabbedPane pane = switch (anchor) {
                case LEFT -> left;
                case RIGHT -> right;
                case BOTTOM -> bottom;
            };
            pane.addTab(title, factory.get());
            pane.setVisible(true);
            revalidate();
        });
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
