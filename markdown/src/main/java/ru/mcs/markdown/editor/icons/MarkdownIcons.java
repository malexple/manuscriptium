package ru.mcs.markdown.editor.icons;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.net.URL;
import javax.swing.UIManager;

/** SVG-иконки встроенного Markdown-модуля с перекраской под текущую тему Swing. */
public final class MarkdownIcons {

    private MarkdownIcons() {
    }

    public static FlatSVGIcon load(String path) {
        URL resource = MarkdownIcons.class.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Markdown icon resource not found: " + path);
        }
        FlatSVGIcon icon = new FlatSVGIcon(resource).derive(16, 16);
        if (!icon.hasFound()) {
            throw new IllegalArgumentException("Cannot load Markdown SVG icon: " + path);
        }
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(MarkdownIcons::recolor));
        return icon;
    }

    private static Color recolor(Component component, Color source) {
        if (source.getRGB() != Color.BLACK.getRGB() && source.getRGB() != Color.WHITE.getRGB()) {
            return source;
        }
        Color foreground = component == null ? null : component.getForeground();
        if (foreground == null) {
            foreground = UIManager.getColor("Label.foreground");
        }
        if (foreground == null) {
            foreground = FlatLaf.isLafDark() ? Color.WHITE : Color.BLACK;
        }
        return new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), source.getAlpha());
    }
}
