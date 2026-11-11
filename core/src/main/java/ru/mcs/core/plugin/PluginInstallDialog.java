package ru.mcs.core.plugin;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import ru.mcs.core.i18n.Messages;

/** Предупреждение перед установкой. Требует явного согласия, а не только нажатия Install. */
final class PluginInstallDialog {

    private PluginInstallDialog() {
    }

    static boolean showDialog(Component owner, PluginInstaller.Inspection inspection, boolean replace) {
        PluginDescriptor d = inspection.descriptor();
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        content.add(label(Messages.get("plugins.install.metadata",
                d.name(), d.version(), d.id(), d.module(), d.sdkVersion())));
        content.add(Box.createVerticalStrut(10));
        if (replace) {
            content.add(label(Messages.get("plugins.install.replace", d.name())));
            content.add(Box.createVerticalStrut(8));
        }
        content.add(label(Messages.get("plugins.install.permissions.title")));
        String permissions = d.permissions().isEmpty()
                ? Messages.get("plugins.permissions.none") : String.join("\n", d.permissions());
        JTextArea permissionText = new JTextArea(permissions);
        permissionText.setEditable(false);
        permissionText.setOpaque(false);
        permissionText.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(permissionText);
        content.add(Box.createVerticalStrut(12));
        content.add(label(Messages.get("plugins.install.warning")));
        content.add(Box.createVerticalStrut(12));
        JCheckBox accepted = new JCheckBox(Messages.get("plugins.install.accept"));
        accepted.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(accepted);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setPreferredSize(new java.awt.Dimension(560, 390));
        while (true) {
            int answer = JOptionPane.showConfirmDialog(owner, scroll, Messages.get("plugins.install.title"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return false;
            }
            if (accepted.isSelected()) {
                return true;
            }
            JOptionPane.showMessageDialog(owner, Messages.get("plugins.install.accept.required"),
                    Messages.get("plugins.install.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
