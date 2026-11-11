package ru.mcs.core.icon;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import javax.swing.UIManager;

/** Загрузка SVG и перекраска монохромных иконок в цвет текущего Swing-компонента. */
public final class Icons {

    public static final int DEFAULT_SIZE = 16;
    private static final String LUCIDE_ROOT = "/ru/mcs/core/icons/lucide/";

    private Icons() {
    }

    /** Загружает встроенную Lucide-иконку размером 16×16. */
    public static FlatSVGIcon lucide(String name) {
        if (name == null || !name.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid Lucide icon name: " + name);
        }
        return load(Icons.class, LUCIDE_ROOT + name + ".svg");
    }

    /**
     * Загружает SVG из ресурсов модуля владельца. Для named-модуля пакет ресурса должен быть открыт,
     * например {@code opens ru.example.plugin.icons;}.
     */
    public static FlatSVGIcon load(Class<?> owner, String path) {
        return load(owner, path, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    public static FlatSVGIcon load(Class<?> owner, String path, int width, int height) {
        Objects.requireNonNull(owner, "owner");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Icon path must not be blank");
        }
        URL resource = owner.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Icon resource not found: " + path + " (owner " + owner.getName() + ")");
        }
        return prepare(new FlatSVGIcon(resource), width, height, path);
    }

    static FlatSVGIcon load(InputStream input, int width, int height, String source) throws IOException {
        return prepare(new FlatSVGIcon(input), width, height, source);
    }

    private static FlatSVGIcon prepare(FlatSVGIcon icon, int width, int height, String source) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Icon size must be positive: " + width + "x" + height);
        }
        FlatSVGIcon sized = icon.derive(width, height);
        if (!sized.hasFound()) {
            throw new IllegalArgumentException("Cannot load SVG icon: " + source);
        }
        sized.setColorFilter(new FlatSVGIcon.ColorFilter(Icons::recolor));
        return sized;
    }

    /** Lucide использует currentColor; SVG-движок передаёт его как чёрный или белый. */
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
