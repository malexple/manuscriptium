package ru.mcs.core.editor;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import ru.mcs.core.i18n.Messages;

/** Неблокирующий preview для ![[image]]; Escape закрывает окно. */
final class ObsidianImagePreview {

    private static final int MAX_WIDTH = 960;
    private static final int MAX_HEIGHT = 640;
    private static final long MAX_RASTER_BYTES = 32L * 1024 * 1024;

    private ObsidianImagePreview() {
    }

    static void show(JComponent anchor, Path file, Icon icon) {
        JLabel label = new JLabel(icon);
        JScrollPane scroll = new JScrollPane(label);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(Math.min(MAX_WIDTH, icon.getIconWidth() + 24),
                Math.min(MAX_HEIGHT, icon.getIconHeight() + 24)));
        java.awt.Window owner = SwingUtilities.getWindowAncestor(anchor);
        JDialog dialog = new JDialog(owner, Messages.get("editor.obsidian.preview.title",
                file.getFileName().toString()), JDialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(scroll);
        installEscape(dialog.getRootPane(), dialog);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    static Icon load(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".svg")) {
            FlatSVGIcon svg = new FlatSVGIcon(file.toUri().toURL());
            if (!svg.hasFound()) {
                throw new IOException("Cannot decode SVG: " + file);
            }
            return scale(svg);
        }
        if (Files.size(file) > MAX_RASTER_BYTES) {
            throw new IOException("Image is larger than 32 MiB: " + file);
        }
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException("Cannot decode image: " + file);
        }
        double scale = scale(image.getWidth(), image.getHeight());
        if (scale >= 1.0) {
            return new ImageIcon(image);
        }
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    private static Icon scale(FlatSVGIcon icon) {
        double scale = scale(icon.getIconWidth(), icon.getIconHeight());
        if (scale >= 1.0) {
            return icon;
        }
        int width = Math.max(1, (int) Math.round(icon.getIconWidth() * scale));
        int height = Math.max(1, (int) Math.round(icon.getIconHeight() * scale));
        return icon.derive(width, height);
    }

    private static double scale(int width, int height) {
        return Math.min(1.0, Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height));
    }

    private static void installEscape(JRootPane root, JDialog dialog) {
        Object key = "mcs.close.obsidian.preview";
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), key);
        root.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dialog.dispose();
            }
        });
    }
}
