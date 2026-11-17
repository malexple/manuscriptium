package ru.mcs.markdown.widget;

import java.awt.Color;
import java.awt.Rectangle;
import javax.swing.JComponent;
import ru.mcs.editor.draw.BlockWidget;
import ru.mcs.editor.draw.DrawSink;

/** Рамка и живой компонент трансклюзии; source-span остаётся только исходным ![[…]] в родительском документе. */
public final class TransclusionWidget extends AbstractBlockWidget {

    private final TransclusionSession session;

    TransclusionWidget(int start, int end, TransclusionSession session) {
        super(start, end);
        this.session = session;
    }

    @Override
    public int height(int width) {
        return session.height(width);
    }

    @Override
    protected void paintWidget(DrawSink sink, Rectangle bounds) {
        sink.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, new Color(0x80, 0x80, 0x80, 24));
        sink.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height, 2, new Color(0x79, 0x6d, 0xaf));
    }

    @Override
    public JComponent component() {
        return session.component();
    }
}
