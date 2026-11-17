package ru.mcs.markdown.render;

import ru.mcs.editor.draw.DrawSink;
import ru.mcs.markdown.layout.BlockBox;
import ru.mcs.markdown.layout.LineBox;

/** Активный блок: исходные Markdown-маркеры остаются видимыми и доступными hit-test. */
public final class SourceBlockRenderer {

    private final InlineRenderer inlineRenderer = new InlineRenderer();

    public void paint(DrawSink sink, BlockBox block) {
        for (LineBox line : block.lines()) {
            inlineRenderer.paint(sink, line);
        }
    }
}
