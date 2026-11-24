package ru.mcs.search;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.net.URL;
import javax.swing.UIManager;

/** SVG-иконки модуля поиска с перекраской под текущую тему Swing. */
final class SearchIcons {

    private SearchIcons() {
    }

    static FlatSVGIcon load(String path) {
        URL resource = SearchIcons.class.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Search icon resource not found: " + path);
        }
        FlatSVGIcon icon = new FlatSVGIcon(resource).derive(16, 16);
        if (!icon.hasFound()) {
            throw new IllegalArgumentException("Cannot load search SVG icon: " + path);
        }
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(SearchIcons::recolor));
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
