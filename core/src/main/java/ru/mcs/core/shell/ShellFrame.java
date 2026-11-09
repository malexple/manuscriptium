package ru.mcs.core.shell;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.ActionMenuBar;
import ru.mcs.core.action.ActionToolbar;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Оболочка: меню и тулбар строятся из ActionManager; панели — временные (срез 2 заменит). */
public final class ShellFrame extends JFrame implements UiHost {

    private final JTabbedPane left = zone(280, 0);
    private final JTabbedPane right = zone(260, 0);
    private final JTabbedPane bottom = zone(0, 200);
    private final JLabel status = new JLabel(" ");

    public ShellFrame(String title, ActionManagerImpl actions) {
        super(title);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Supplier<DataContext> ctx = () -> DataContext.EMPTY;

        setJMenuBar(new ActionMenuBar(actions, ctx));
        add(new ActionToolbar(actions, ActionIds.MAIN_TOOLBAR, ActionPlaces.MAIN_TOOLBAR, ctx), BorderLayout.NORTH);
        add(new JLabel("Editor area (next slices)", SwingConstants.CENTER), BorderLayout.CENTER);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
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
