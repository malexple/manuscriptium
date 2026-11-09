package ru.mcs.core.toolwindow;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import ru.mcs.spi.ui.ToolWindowAnchor;

/**
 * Область окна: stripes по краям и вложенные JSplitPane вокруг центральной зоны.
 * На каждой стороне активна не более одной панели. Размеры сторон запоминаются при перетаскивании разделителя.
 */
public final class DockingPanel extends JPanel {

    private final ToolWindowManagerImpl manager;
    private final Stripe left = new Stripe(ToolWindowAnchor.LEFT);
    private final Stripe right = new Stripe(ToolWindowAnchor.RIGHT);
    private final Stripe bottom = new Stripe(ToolWindowAnchor.BOTTOM);
    private final JPanel body = new JPanel(new BorderLayout());
    private JComponent center = new JPanel();

    public DockingPanel(ToolWindowManagerImpl manager) {
        super(new BorderLayout());
        this.manager = manager;
        add(body, BorderLayout.CENTER);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        refresh();
        manager.addChangeListener(() -> SwingUtilities.invokeLater(this::refresh));
    }

    public void setCenter(JComponent component) {
        this.center = component;
        relayout();
    }

    private void refresh() {
        left.rebuild(manager);
        right.rebuild(manager);
        bottom.rebuild(manager);
        relayout();
    }

    private void relayout() {
        JComponent inner = center;
        ToolWindowImpl l = manager.activeAt(ToolWindowAnchor.LEFT);
        ToolWindowImpl r = manager.activeAt(ToolWindowAnchor.RIGHT);
        ToolWindowImpl b = manager.activeAt(ToolWindowAnchor.BOTTOM);
        if (r != null) {
            inner = split(JSplitPane.HORIZONTAL_SPLIT, inner, new ToolWindowPanel(r), 1.0, ToolWindowAnchor.RIGHT);
        }
        if (l != null) {
            inner = split(JSplitPane.HORIZONTAL_SPLIT, new ToolWindowPanel(l), inner, 0.0, ToolWindowAnchor.LEFT);
        }
        if (b != null) {
            inner = split(JSplitPane.VERTICAL_SPLIT, inner, new ToolWindowPanel(b), 1.0, ToolWindowAnchor.BOTTOM);
        }
        body.removeAll();
        body.add(inner, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    private JSplitPane split(int orientation, JComponent first, JComponent second, double weight,
                             ToolWindowAnchor anchor) {
        JComponent side = anchor == ToolWindowAnchor.LEFT ? first : second;
        int size = manager.size(anchor);
        side.setPreferredSize(anchor == ToolWindowAnchor.BOTTOM ? new Dimension(0, size) : new Dimension(size, 0));

        JSplitPane pane = new JSplitPane(orientation, first, second);
        pane.setResizeWeight(weight);
        pane.setContinuousLayout(true);
        pane.setBorder(null);
        pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> rememberSize(pane, anchor));
        return pane;
    }

    private void rememberSize(JSplitPane pane, ToolWindowAnchor anchor) {
        int total = pane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? pane.getWidth() : pane.getHeight();
        if (total <= 0) {
            return;
        }
        int location = pane.getDividerLocation();
        int size = anchor == ToolWindowAnchor.LEFT ? location : total - location - pane.getDividerSize();
        if (size > 40) {
            manager.setSize(anchor, size);
        }
    }
}
