package ru.mcs.plugin.mdeditor.widget;

import java.awt.Font;
import java.awt.Rectangle;
import ru.mcs.plugin.mdeditor.draw.BlockWidget;
import ru.mcs.plugin.mdeditor.draw.DrawSink;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Изображение, полученное через BlockRendererExtension (mermaid/sequence/flow и др.). */
public final class CodeBlockWidget extends AbstractBlockWidget {

    private final WidgetRegistry.AsyncImage result;
    private final ComputedStyle style;
    private final Font font;
    private final String info;

    CodeBlockWidget(int start, int end, WidgetRegistry.ExtensionKey key,
                    WidgetRegistry registry, ComputedStyle style, Font font) {
        super(start, end);
        this.result = registry.extension(key);
        this.style = style;
        this.font = font;
        this.info = key.info();
    }

    @Override
    public int height(int width) {
        if (result.image == null) {
            return WidgetText.lineHeight(font) + 20;
        }
        float ratio = Math.min(1f, (float) width / result.image.getWidth());
        return Math.max(1, Math.round(result.image.getHeight() * ratio));
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        if (result.image != null) {
            float ratio = Math.min(1f, (float) bounds.width / result.image.getWidth());
            sink.drawImage(result.image, bounds.x, bounds.y,
                    Math.round(result.image.getWidth() * ratio), Math.round(result.image.getHeight() * ratio));
            return;
        }
        WidgetText.draw(sink, result.failed ? "Cannot render " + info : "Rendering " + info + "…",
                font, style.color(), bounds.x + 8, bounds.y + 10 + WidgetText.baseline(font));
    }
}
