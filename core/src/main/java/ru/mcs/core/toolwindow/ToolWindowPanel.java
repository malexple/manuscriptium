package ru.mcs.core.toolwindow;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/** Контейнер активной панели: заголовок с кнопкой «скрыть» и содержимое. */
final class ToolWindowPanel extends JPanel {

    ToolWindowPanel(ToolWindowImpl tw) {
        super(new BorderLayout());
        JLabel title = new JLabel(tw.getTitle());
        title.setBorder(new EmptyBorder(3, 8, 3, 4));

        JButton hide = new JButton("\u2014");
        hide.setToolTipText("Hide");
        hide.setFocusable(false);
        hide.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        hide.addActionListener(e -> tw.hide());

        Color line = UIManager.getColor("Separator.foreground");
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new MatteBorder(0, 0, 1, 0, line != null ? line : Color.LIGHT_GRAY));
        header.add(title, BorderLayout.WEST);
        header.add(hide, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(tw.content(), BorderLayout.CENTER);
    }
}
