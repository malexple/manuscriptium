package ru.mcs.markdown.editor.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import ru.mcs.markdown.editor.MarkdownMessages;
import ru.mcs.markdown.editor.TableDialog;
import ru.mcs.markdown.widget.TableModel;

/**
 * Панель над таблицей с кареткой: размер, выравнивание, More Actions, удаление.
 * Рисуется Canvas-ом в координатах содержимого вместе с таблицей и принимает клики через {@code pressOverlay}.
 */
final class TableToolbar {

    static final int HEIGHT = 22;
    private static final int BUTTON = 26;
    private static final int GROUP_GAP = 6;

    enum Action {
        RESIZE(TableToolbarIcon.Kind.GRID),
        ALIGN_LEFT(TableToolbarIcon.Kind.ALIGN_LEFT),
        ALIGN_CENTER(TableToolbarIcon.Kind.ALIGN_CENTER),
        ALIGN_RIGHT(TableToolbarIcon.Kind.ALIGN_RIGHT),
        SORT_ASC(TableToolbarIcon.Kind.SORT_ASC),
        SORT_DESC(TableToolbarIcon.Kind.SORT_DESC),
        MORE(TableToolbarIcon.Kind.MORE),
        DELETE(TableToolbarIcon.Kind.DELETE);

        private final TableToolbarIcon.Kind kind;

        Action(TableToolbarIcon.Kind kind) {
            this.kind = kind;
        }
    }

    record Slot(Action action, Rectangle bounds) {
    }

    private final MarkdownCanvas canvas;
    private TableModel.Alignment shown = TableModel.Alignment.LEFT;

    TableToolbar(MarkdownCanvas canvas) {
        this.canvas = canvas;
    }

    /** Кнопки в области панели: слева размер, выравнивание и сортировка, справа More Actions и корзина. */
    static List<Slot> slots(Rectangle area) {
        List<Slot> result = new ArrayList<>();
        int x = area.x;
        result.add(slot(Action.RESIZE, x, area));
        x += BUTTON + GROUP_GAP;
        for (Action action : new Action[]{Action.ALIGN_LEFT, Action.ALIGN_CENTER, Action.ALIGN_RIGHT}) {
            result.add(slot(action, x, area));
            x += BUTTON;
        }
        x += GROUP_GAP;
        for (Action action : new Action[]{Action.SORT_ASC, Action.SORT_DESC}) {
            result.add(slot(action, x, area));
            x += BUTTON;
        }
        int right = area.x + area.width - BUTTON;
        result.add(slot(Action.MORE, right - BUTTON, area));
        result.add(slot(Action.DELETE, right, area));
        return result;
    }

    private static Slot slot(Action action, int x, Rectangle area) {
        return new Slot(action, new Rectangle(x, area.y, BUTTON, area.height));
    }

    void paint(Graphics2D graphics, Rectangle area, Color foreground) {
        TableController.State state = TableController.state(canvas);
        if (state != null) {
            shown = state.alignment();
        }
        Action current = switch (shown) {
            case LEFT -> Action.ALIGN_LEFT;
            case CENTER -> Action.ALIGN_CENTER;
            case RIGHT -> Action.ALIGN_RIGHT;
        };
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (Slot slot : slots(area)) {
                Rectangle bounds = slot.bounds();
                if (slot.action() == current) {
                    g.setColor(new Color(128, 128, 128, 70));
                    g.fillRoundRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2, 6, 6);
                }
                new TableToolbarIcon(slot.action().kind, () -> foreground).paintIcon(canvas, g,
                        bounds.x + (bounds.width - 14) / 2, bounds.y + (bounds.height - 14) / 2);
            }
        } finally {
            g.dispose();
        }
    }

    boolean press(Rectangle area, Point contentPoint) {
        for (Slot slot : slots(area)) {
            if (slot.bounds().contains(contentPoint)) {
                run(slot);
                return true;
            }
        }
        return false;
    }

    private void run(Slot slot) {
        switch (slot.action()) {
            case RESIZE -> resize();
            case ALIGN_LEFT -> canvas.alignTableColumn(TableModel.Alignment.LEFT);
            case ALIGN_CENTER -> canvas.alignTableColumn(TableModel.Alignment.CENTER);
            case ALIGN_RIGHT -> canvas.alignTableColumn(TableModel.Alignment.RIGHT);
            case SORT_ASC -> TableController.sort(canvas, true);
            case SORT_DESC -> TableController.sort(canvas, false);
            case MORE -> showMenu(slot.bounds());
            case DELETE -> TableController.deleteTable(canvas);
        }
        canvas.requestFocusInWindow();
        canvas.repaint();
    }

    private void resize() {
        TableController.State state = TableController.state(canvas);
        if (state == null) {
            return;
        }
        TableDialog.show(canvas, state.columns(), state.rows(), "table.resize.title")
                .ifPresent(size -> TableController.resize(canvas, size.columns(), size.rows()));
    }

    private void showMenu(Rectangle buttonBounds) {
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
        boolean sortable = state != null && state.rows() > 2;
        item(menu, "table.menu.sortAsc", sortable, () -> TableController.sort(canvas, true));
        item(menu, "table.menu.sortDesc", sortable, () -> TableController.sort(canvas, false));
        menu.addSeparator();
        item(menu, "table.menu.copy", state != null, () -> TableController.copy(canvas));
        item(menu, "table.menu.format", state != null, () -> TableController.format(canvas));
        menu.addSeparator();
        item(menu, "table.menu.delete", state != null, () -> TableController.deleteTable(canvas));
        Point anchor = canvas.toComponent(new Point(buttonBounds.x + buttonBounds.width,
                buttonBounds.y + buttonBounds.height));
        menu.show(canvas, anchor.x - menu.getPreferredSize().width, anchor.y);
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
}
