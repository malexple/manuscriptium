package ru.mcs.core.statusbar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import ru.mcs.spi.ui.StatusBarWidget;

/** Статус-бар: сообщение и левые виджеты слева; правые виджеты и индикатор задач справа. */
public final class StatusBarPanel extends JPanel {

    private final StatusBarModel model;
    private final JLabel message = new JLabel(" ");
    private final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    private final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    private final JLabel taskLabel = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final Map<StatusBarWidget, JComponent> components = new IdentityHashMap<>();
    private final Timer clearTimer;
    private String shownMessage = "";

    public StatusBarPanel(StatusBarModel model) {
        super(new BorderLayout());
        this.model = model;
        this.clearTimer = new Timer(10_000, e -> this.model.setMessage(""));
        Color line = UIManager.getColor("Separator.foreground");
        setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, line != null ? line : Color.LIGHT_GRAY),
                new EmptyBorder(2, 6, 2, 6)));
        left.setOpaque(false);
        right.setOpaque(false);
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(90, 10));
        clearTimer.setRepeats(false);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
        refresh();
        model.addChangeListener(() -> {
            if (SwingUtilities.isEventDispatchThread()) {
                refresh();
            } else {
                SwingUtilities.invokeLater(this::refresh);
            }
        });
    }

    private void refresh() {
        String text = model.message();
        message.setText(text.isBlank() ? " " : text);
        if (!text.equals(shownMessage)) {
            shownMessage = text;
            if (text.isBlank()) {
                clearTimer.stop();
            } else {
                clearTimer.restart();
            }
        }

        left.removeAll();
        right.removeAll();
        left.add(message);
        List<StatusBarModel.Entry> entries = model.widgets();
        components.keySet().removeIf(w -> entries.stream().noneMatch(e -> e.widget() == w));
        for (StatusBarModel.Entry entry : entries) {
            JComponent component = components.computeIfAbsent(entry.widget(), StatusBarPanel::create);
            if (entry.widget().getPosition() == StatusBarWidget.Position.LEFT) {
                left.add(component);
            } else {
                right.add(component);
            }
        }
        List<StatusBarModel.Task> tasks = model.tasks();
        if (!tasks.isEmpty()) {
            String extra = tasks.size() > 1 ? " (+" + (tasks.size() - 1) + ")" : "";
            taskLabel.setText(tasks.get(tasks.size() - 1).title() + extra);
            right.add(taskLabel);
            right.add(progress);
        }
        revalidate();
        repaint();
    }

    private static JComponent create(StatusBarWidget widget) {
        try {
            JComponent component = widget.createComponent();
            return component != null ? component : new JLabel("?");
        } catch (Throwable t) {
            return new JLabel("! " + widget.getId());
        }
    }
}
