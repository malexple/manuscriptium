package ru.mcs.plugin.mdeditor.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import ru.mcs.plugin.mdeditor.draw.DrawSink;
import ru.mcs.plugin.mdeditor.draw.Graphics2DDrawSink;
import ru.mcs.plugin.mdeditor.layout.BlockBox;
import ru.mcs.plugin.mdeditor.layout.LayoutResult;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.mode.FocusMode;
import ru.mcs.plugin.mdeditor.model.Range;
import ru.mcs.spi.swing.editor.style.ComputedStyle;

/** Рисует готовый Markdown layout, не участвуя в расчёте координат и source offsets. */
public final class MarkdownRenderer {

    private final MarkdownLayoutEngine layout;
    private final BlockRenderer blockRenderer = new BlockRenderer();
    private final SourceBlockRenderer sourceRenderer = new SourceBlockRenderer();
    private final FocusMode focusMode = new FocusMode();

    public MarkdownRenderer(MarkdownLayoutEngine layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    public void setFocusMode(boolean enabled) {
        focusMode.setEnabled(enabled);
    }

    public boolean focusMode() {
        return focusMode.isEnabled();
    }

    public void paint(Graphics2D graphics, Rectangle clip, Range selection) {
        LayoutResult result = layout.layout();
        List<BlockBox> visible = result.blocks().stream()
                .filter(block -> block.y() <= clip.y + clip.height && block.y() + block.height() >= clip.y)
                .toList();
        ComputedStyle root = layout.rootStyle();
        for (BlockBox block : visible) {
            if (!block.source()) {
                Graphics2D target = dimmed(graphics, block, root);
                try {
                    blockRenderer.paintBackground(new Graphics2DDrawSink(target), block);
                } finally {
                    if (target != graphics) {
                        target.dispose();
                    }
                }
            }
        }
        if (selection != null && selection.length() > 0) {
            graphics.setColor(selectionColor(root));
            for (Rectangle rectangle : layout.selectionRects(selection, clip)) {
                graphics.fill(rectangle);
            }
        }
        for (BlockBox block : visible) {
            Graphics2D target = dimmed(graphics, block, root);
            try {
                DrawSink sink = new Graphics2DDrawSink(target);
                if (block.source()) {
                    sourceRenderer.paint(sink, block);
                } else {
                    blockRenderer.paintContent(sink, block);
                }
            } finally {
                if (target != graphics) {
                    target.dispose();
                }
            }
        }
    }

    private Graphics2D dimmed(Graphics2D graphics, BlockBox block, ComputedStyle root) {
        return focusMode.isEnabled() && !block.source() ? focusMode.wrap(graphics, root) : graphics;
    }

    private static Color selectionColor(ComputedStyle root) {
        String value = root.variables().get("--select-text-bg-color");
        if (value != null && value.matches("#[0-9a-fA-F]{6}")) {
            return new Color(Integer.parseInt(value.substring(1), 16));
        }
        return new Color(0xb6d7ff);
    }
}
