package ru.mcs.core.shell;

import java.awt.BorderLayout;
import java.util.function.Supplier;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.ActionMenuBar;
import ru.mcs.core.action.ActionToolbar;
import ru.mcs.core.toolwindow.DockingPanel;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.DataContext;

/** Оболочка: меню и тулбар из ActionManager, панели и stripes из ToolWindowManager. */
public final class ShellFrame extends JFrame {

    private final JLabel status = new JLabel(" ");
    private final DockingPanel docking;

    public ShellFrame(String title, ActionManagerImpl actions, ToolWindowManagerImpl toolWindows) {
        super(title);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Supplier<DataContext> ctx = () -> DataContext.EMPTY;

        setJMenuBar(new ActionMenuBar(actions, ctx));
        add(new ActionToolbar(actions, ActionIds.MAIN_TOOLBAR, ActionPlaces.MAIN_TOOLBAR, ctx), BorderLayout.NORTH);

        docking = new DockingPanel(toolWindows);
        docking.setCenter(new JLabel("Editor area (next slices)", SwingConstants.CENTER));
        add(docking, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    public DockingPanel docking() {
        return docking;
    }

    public void setStatus(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            status.setText(" " + text);
        } else {
            SwingUtilities.invokeLater(() -> status.setText(" " + text));
        }
    }
}
