package ru.mcs.plugin.mdeditor.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import ru.mcs.editor.draw.BlockWidget;
import ru.mcs.editor.draw.DrawSink;
import ru.mcs.spi.swing.editor.style.ComputedStyle;

/** Блочное изображение с placeholder, относительным путём и Obsidian-resize handle. */
public final class ImageWidget extends AbstractBlockWidget {

    private static final int HANDLE = 12;

    private final ImageSource source;
    private final Path file;
    private final WidgetRegistry registry;
    private final ComputedStyle style;
    private final Font font;
    private final String caption;
    private int renderedWidth;
    private int renderedHeight;

    ImageWidget(int start, int end, ImageSource source, Path baseDirectory,
                WidgetRegistry registry, ComputedStyle style, Font font, String label) {
        super(start, end);
        this.source = source;
        this.registry = registry;
        this.style = style;
        this.font = font;
        String alt = source.alt() == null ? "" : source.alt().strip();
        this.caption = label == null ? null : label + (alt.isEmpty() ? "" : ". " + alt);
        file = resolve(baseDirectory, source.path());
    }

    @Override
    public int height(int width) {
        BufferedImage image = image();
        if (image == null) {
            int placeholder = file == null || entry().failed()
                    ? Math.max(40, WidgetText.lineHeight(font) + 20) : 96;
            return placeholder + captionHeight();
        }
        renderedWidth = displayWidth(image, width);
        renderedHeight = Math.max(1, Math.round((float) image.getHeight() * renderedWidth / image.getWidth()));
        return renderedHeight + captionHeight();
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        BufferedImage image = image();
        if (image != null) {
            int width = displayWidth(image, bounds.width);
            int height = Math.max(1, Math.round((float) image.getHeight() * width / image.getWidth()));
            sink.drawImage(image, bounds.x, bounds.y, width, height);
            Color handle = new Color(style.color().getRed(), style.color().getGreen(), style.color().getBlue(), 180);
            sink.fillRect(bounds.x + width - HANDLE, bounds.y + height - HANDLE, HANDLE, HANDLE, handle);
            paintCaption(sink, bounds);
            return;
        }
        sink.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height - captionHeight(), 6,
                style.backgroundColor() != null ? style.backgroundColor() : new Color(0xf6f8fa));
        String text = file == null ? "Image outside the note directory: " + source.path()
                : entry().failed() ? "Cannot load image: " + source.path() : "Loading image…";
        WidgetText.draw(sink, text, font, style.color(), bounds.x + 10,
                bounds.y + 10 + WidgetText.baseline(font));
        paintCaption(sink, bounds);
    }

    private void paintCaption(DrawSink sink, Rectangle bounds) {
        if (caption != null) {
            Font captionFont = font.deriveFont(Math.max(12f, font.getSize2D() - 1f));
            WidgetText.draw(sink, caption, captionFont, style.color(), bounds.x,
                    bounds.y + bounds.height - WidgetText.lineHeight(captionFont)
                            + WidgetText.baseline(captionFont));
        }
    }

    private int captionHeight() {
        return caption == null ? 0 : WidgetText.lineHeight(font) + 4;
    }

    public boolean resizeHandleAt(Point point) {
        Rectangle bounds = bounds();
        return image() != null && point.x >= bounds.x + renderedWidth - HANDLE - 4
                && point.x <= bounds.x + renderedWidth + 4
                && point.y >= bounds.y + renderedHeight - HANDLE - 4
                && point.y <= bounds.y + renderedHeight + 4;
    }

    public int resizeWidth(Point dragged, Point pressed) {
        Rectangle bounds = bounds();
        int delta = dragged.x - pressed.x;
        return Math.max(16, Math.min(bounds.width, renderedWidth + delta));
    }

    public String resizedSource(int width) {
        return source.withWidth(width);
    }

    public ImageSource source() {
        return source;
    }

    private BufferedImage image() {
        return file == null ? null : entry().image();
    }

    private ImageCache.Entry entry() {
        return file == null ? ImageCache.missing() : registry.image(file);
    }

    private int displayWidth(BufferedImage image, int available) {
        int requested = source.width() == null ? image.getWidth() : source.width();
        return Math.max(1, Math.min(Math.max(1, available), requested));
    }

    private static Path resolve(Path base, String value) {
        if (base == null || value == null || value.isBlank() || value.matches("^[a-zA-Z]+://.*")) {
            return null;
        }
        Path relative = Path.of(value);
        if (relative.isAbsolute()) {
            return null;
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relative).normalize();
        return resolved.startsWith(normalizedBase) ? resolved : null;
    }
}
