package ru.mcs.plugin.mdeditor.render;

import ru.mcs.plugin.mdeditor.layout.BlockBox;
import ru.mcs.plugin.mdeditor.layout.LineBox;

/** Активный блок: исходные Markdown-маркеры остаются видимыми и доступными hit-test. */
public final class SourceBlockRenderer {

    private final InlineRenderer inlineRenderer = new InlineRenderer();

    public void paint(DrawSink sink, BlockBox block) {
        for (LineBox line : block.lines()) {
            inlineRenderer.paint(sink, line);
        }
    }
}
