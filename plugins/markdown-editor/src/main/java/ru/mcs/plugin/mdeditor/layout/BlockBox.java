package ru.mcs.plugin.mdeditor.layout;

import java.util.List;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Готовый к рисованию блок с абсолютными canvas-координатами. */
public final class BlockBox {

    private final BlockSpan span;
    private final ComputedStyle style;
    private final List<LineBox> lines;
    private final boolean source;
    private final String info;
    private int x;
    private int y;
    private int width;
    private int height;
    private int contentX;
    private int contentWidth;

    BlockBox(BlockSpan span, ComputedStyle style, List<LineBox> lines, boolean source, String info) {
        this.span = span;
        this.style = style;
        this.lines = List.copyOf(lines);
        this.source = source;
        this.info = info;
    }

    void place(int x, int y, int width, int height, int contentX, int contentWidth) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.contentX = contentX;
        this.contentWidth = contentWidth;
    }

    public BlockSpan span() {
        return span;
    }

    public ComputedStyle style() {
        return style;
    }

    public List<LineBox> lines() {
        return lines;
    }

    public boolean source() {
        return source;
    }

    public String info() {
        return info;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int contentX() {
        return contentX;
    }

    public int contentWidth() {
        return contentWidth;
    }
}
