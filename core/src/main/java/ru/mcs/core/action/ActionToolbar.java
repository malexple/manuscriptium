package ru.mcs.core.action;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import ru.mcs.spi.action.ActionGroup;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.action.Presentation;

/**
 * Тулбар из группы действий. Обновляется при изменении реестра и после выполнения действия;
 * вызывайте {@link #refresh()} при смене контекста (позже это будет делать MessageBus).
 */
public final class ActionToolbar extends JToolBar {

    private final ActionManagerImpl actions;
    private final String groupId;
    private final String place;
    private final Supplier<DataContext> ctx;

    public ActionToolbar(ActionManagerImpl actions, String groupId, String place, Supplier<DataContext> ctx) {
        this.actions = actions;
        this.groupId = groupId;
        this.place = place;
        this.ctx = ctx;
        setFloatable(false);
        refresh();
        actions.addChangeListener(() -> SwingUtilities.invokeLater(this::refresh));
    }

    public void refresh() {
        removeAll();
        if (actions.getAction(groupId) instanceof ActionGroup g) {
            fill(g);
        }
        int n = getComponentCount();
        if (n > 0 && getComponent(n - 1) instanceof JToolBar.Separator) {
            remove(n - 1);
        }
        setVisible(getComponentCount() > 0);
        revalidate();
        repaint();
    }

    private void fill(ActionGroup group) {
        AnActionEvent ge = ActionUtil.update(group, place, ActionUtil.context(ctx));
        if (!ge.getPresentation().isVisible()) {
            return;
        }
        for (AnAction child : ActionUtil.children(group, ge)) {
            if (child instanceof ru.mcs.spi.action.Separator) {
                int n = getComponentCount();
                if (n > 0 && !(getComponent(n - 1) instanceof JToolBar.Separator)) {
                    addSeparator();
                }
            } else if (child instanceof ActionGroup sub) {
                fill(sub);
            } else {
                Component button = createButton(child);
                if (button != null) {
                    add(button);
                }
            }
        }
    }

    private Component createButton(AnAction action) {
        Presentation p = ActionUtil.update(action, place, ActionUtil.context(ctx)).getPresentation();
        if (!p.isVisible()) {
            return null;
        }
        JButton button = new JButton(p.getIcon());
        if (p.getIcon() == null) {
            button.setText(p.getText());
        }
        button.setToolTipText(p.getDescription() != null ? p.getDescription() : p.getText());
        button.setEnabled(p.isEnabled());
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.setFocusable(false);
        button.addActionListener(ev -> {
            ActionUtil.perform(action, place, ActionUtil.context(ctx));
            SwingUtilities.invokeLater(this::refresh);
        });
        return button;
    }
}
