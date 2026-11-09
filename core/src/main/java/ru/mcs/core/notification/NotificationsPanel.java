package ru.mcs.core.notification;

import java.awt.BorderLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Содержимое панели Notifications: история уведомлений. */
public final class NotificationsPanel extends JPanel {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public NotificationsPanel(NotificationManagerImpl manager) {
        super(new BorderLayout());
        DefaultListModel<String> model = new DefaultListModel<>();
        manager.history().forEach(n -> model.addElement(format(n)));
        JList<String> list = new JList<>(model);
        add(new JScrollPane(list), BorderLayout.CENTER);
        manager.addListener(n -> SwingUtilities.invokeLater(() -> {
            model.addElement(format(n));
            list.ensureIndexIsVisible(model.size() - 1);
        }));
    }

    static String format(NotificationManagerImpl.Notice n) {
        return "[" + TIME.format(n.time()) + "] " + n.type() + " (" + n.source() + "): "
                + n.title() + " \u2014 " + n.content();
    }
}
