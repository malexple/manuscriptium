package ru.mcs.plugin.mdeditor.widget;

import java.awt.Rectangle;
import ru.mcs.plugin.mdeditor.render.DrawSink;

abstract class AbstractBlockWidget implements BlockWidget {

    private final int sourceStart;
    private final int sourceEnd;
    private Rectangle bounds = new Rectangle();

    AbstractBlockWidget(int sourceStart, int sourceEnd) {
        this.sourceStart = sourceStart;
        this.sourceEnd = sourceEnd;
    }

    @Override
    public final void paint(DrawSink sink, int x, int y, int width) {
        int height = height(width);
        bounds = new Rectangle(x, y, width, height);
        paintWidget(sink, new Rectangle(bounds));
    }

    protected abstract void paintWidget(DrawSink sink, Rectangle bounds);

    @Override
    public final Rectangle bounds() {
        return new Rectangle(bounds);
    }

    @Override
    public final int sourceStart() {
        return sourceStart;
    }

    @Override
    public final int sourceEnd() {
        return sourceEnd;
    }
}
