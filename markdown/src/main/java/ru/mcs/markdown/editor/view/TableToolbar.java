package ru.mcs.markdown.editor.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.function.BooleanSupplier;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import ru.mcs.markdown.editor.MarkdownMessages;
import ru.mcs.markdown.editor.TableDialog;
import ru.mcs.markdown.widget.TableModel;

/** Панель над таблицей, пока в ней каретка: размер, выравнивание, More Actions, удаление. */
final class TableToolbar extends JPanel {

    static final int HEIGHT = 20;

    private final MarkdownCanvas canvas;
    private final ToolbarButton left;
    private final ToolbarButton center;
    private final ToolbarButton right;
    private TableModel.Alignment shown;

    TableToolbar(MarkdownCanvas canvas) {
        super(new BorderLayout());
        this.canvas = canvas;
        setName("markdown.table.toolbar");
        setOpaque(false);
        ToolbarButton resize = button(TableToolbarIcon.Kind.GRID, "table.toolbar.resize",
                "markdown.table.resize", this::resize);
        left = button(TableToolbarIcon.Kind.ALIGN_LEFT, "table.toolbar.alignLeft", "markdown.table.alignLeft",
                () -> canvas.alignTableColumn(TableModel.Alignment.LEFT));
        center = button(TableToolbarIcon.Kind.ALIGN_CENTER, "table.toolbar.alignCenter",
                "markdown.table.alignCenter", () -> canvas.alignTableColumn(TableModel.Alignment.CENTER));
        right = button(TableToolbarIcon.Kind.ALIGN_RIGHT, "table.toolbar.alignRight",
                "markdown.table.alignRight", () -> canvas.alignTableColumn(TableModel.Alignment.RIGHT));
        ToolbarButton more = button(TableToolbarIcon.Kind.MORE, "table.toolbar.more", "markdown.table.more", null);
        more.addActionListener(event -> {
            showMenu(more);
            canvas.requestFocusInWindow();
        });
        ToolbarButton delete = button(TableToolbarIcon.Kind.DELETE, "table.toolbar.delete",
                "markdown.table.delete", () -> TableController.deleteTable(canvas));

        JPanel west = group();
        west.add(resize);
        west.add(Box.createHorizontalStrut(6));
        west.add(left);
        west.add(center);
        west.add(right);
        JPanel east = group();
        east.add(more);
        east.add(delete);
        add(west, BorderLayout.WEST);
        add(east, BorderLayout.EAST);
    }

    /** Подсвечивает выравнивание колонки под кареткой; без изменений не перерисовывает кнопки. */
    void refresh() {
        TableController.State state = TableController.state(canvas);
        if (state == null || state.alignment() == shown) {
            return;
        }
        shown = state.alignment();
        left.setActive(shown == TableModel.Alignment.LEFT);
        center.setActive(shown == TableModel.Alignment.CENTER);
        right.setActive(shown == TableModel.Alignment.RIGHT);
    }

    private void resize() {
        TableController.State state = TableController.state(canvas);
        if (state == null) {
            return;
        }
        TableDialog.show(canvas, state.columns(), state.rows(), "table.resize.title")
                .ifPresent(size -> TableController.resize(canvas, size.columns(), size.rows()));
    }

    private void showMenu(Component anchor) {
        TableController.State state = TableController.state(canvas);
        boolean bodyRow = state != null && state.row() > 0;
        JPopupMenu menu = new JPopupMenu();
        menu.setName("markdown.table.menu");
        item(menu, "table.menu.rowBefore", bodyRow, () -> TableController.addRowBefore(canvas));
        item(menu, "table.menu.rowAfter", state != null, canvas::addTableRow);
        menu.addSeparator();
        item(menu, "table.menu.columnBefore", state != null, () -> TableController.addColumnBefore(canvas));
        item(menu, "table.menu.columnAfter", state != null, canvas::addTableColumn);
        menu.addSeparator();
        item(menu, "table.menu.deleteRow", bodyRow && state.rows() > 1, canvas::removeTableRow);
        item(menu, "table.menu.deleteColumn", state != null && state.columns() > 1, canvas::removeTableColumn);
        menu.addSeparator();
        item(menu, "table.menu.copy", state != null, () -> TableController.copy(canvas));
        item(menu, "table.menu.format", state != null, () -> TableController.format(canvas));
        menu.addSeparator();
        item(menu, "table.menu.delete", state != null, () -> TableController.deleteTable(canvas));
        menu.show(anchor, anchor.getWidth() - menu.getPreferredSize().width, anchor.getHeight());
    }

    private void item(JPopupMenu menu, String key, boolean enabled, BooleanSupplier action) {
        JMenuItem item = new JMenuItem(MarkdownMessages.get(key));
        item.setName(key);
        item.setEnabled(enabled);
        item.addActionListener(event -> {
            action.getAsBoolean();
            canvas.requestFocusInWindow();
        });
        menu.add(item);
    }

    private ToolbarButton button(TableToolbarIcon.Kind kind, String tooltipKey, String name, Runnable action) {
        ToolbarButton button = new ToolbarButton(new TableToolbarIcon(kind, canvas::getForeground));
        button.setName(name);
        button.setToolTipText(MarkdownMessages.get(tooltipKey));
        if (action != null) {
            button.addActionListener(event -> {
                action.run();
                canvas.requestFocusInWindow();
            });
        }
        return button;
    }

    private static JPanel group() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        panel.setOpaque(false);
        return panel;
    }

    /** Плоская кнопка без фокуса: каретка остаётся в таблице; фон только при наведении и для текущего выравнивания. */
    private static final class ToolbarButton extends JButton {

        private boolean active;

        ToolbarButton(TableToolbarIcon icon) {
            super(icon);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setMargin(new Insets(1, 3, 1, 3));
            setPreferredSize(new Dimension(24, HEIGHT));
        }

        void setActive(boolean active) {
            if (this.active != active) {
                this.active = active;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (active || getModel().isRollover()) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(128, 128, 128, active ? 70 : 40));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                } finally {
                    g.dispose();
                }
            }
            super.paintComponent(graphics);
        }
    }
}
