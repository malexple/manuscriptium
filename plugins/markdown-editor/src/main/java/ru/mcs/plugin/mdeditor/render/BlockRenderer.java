package ru.mcs.plugin.mdeditor.render;

import java.awt.Color;
import ru.mcs.editor.draw.DrawSink;
import ru.mcs.plugin.mdeditor.layout.BlockBox;
import ru.mcs.plugin.mdeditor.layout.LineBox;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.swing.editor.style.CssBorder;

/** Тематическое оформление блока: фон, radius, границы и inline-содержимое. */
public final class BlockRenderer {

    private final InlineRenderer inlineRenderer = new InlineRenderer();

    public void paint(DrawSink sink, BlockBox block) {
        paintBackground(sink, block);
        paintContent(sink, block);
    }

    public void paintBackground(DrawSink sink, BlockBox block) {
        ComputedStyle style = block.style();
        int radius = px(style.borderRadius().value());
        if (style.backgroundColor().getAlpha() > 0) {
            if (radius > 0) {
                sink.fillRoundRect(block.x(), block.y(), block.width(), block.height(), radius,
                        style.backgroundColor());
            } else {
                sink.fillRect(block.x(), block.y(), block.width(), block.height(), style.backgroundColor());
            }
        }
    }

    public void paintContent(DrawSink sink, BlockBox block) {
        ComputedStyle style = block.style();
        if (block.span().kind() == BlockKind.THEMATIC_BREAK) {
            CssBorder border = style.borders().bottom();
            int y = block.y() + block.height() / 2;
            sink.drawLine(block.x(), y, block.x() + block.width(), y,
                    Math.max(1, px(border.width().value())), border.color());
            return;
        }
        paintBorder(sink, block, style.borders().left(), 3);
        paintBorder(sink, block, style.borders().top(), 0);
        paintBorder(sink, block, style.borders().right(), 1);
        paintBorder(sink, block, style.borders().bottom(), 2);
        if (block.widget() != null) {
            block.widget().paint(sink, block.contentX(), block.contentY(), block.contentWidth());
        }
        for (LineBox line : block.lines()) {
            inlineRenderer.paint(sink, line);
        }
    }

    private static void paintBorder(DrawSink sink, BlockBox block, CssBorder border, int side) {
        if (!border.visible()) {
            return;
        }
        int thickness = Math.max(1, px(border.width().value()));
        Color color = border.color();
        switch (side) {
            case 0 -> sink.drawLine(block.x(), block.y(), block.x() + block.width(), block.y(), thickness, color);
            case 1 -> sink.drawLine(block.x() + block.width() - thickness, block.y(),
                    block.x() + block.width() - thickness, block.y() + block.height(), thickness, color);
            case 2 -> sink.drawLine(block.x(), block.y() + block.height() - thickness,
                    block.x() + block.width(), block.y() + block.height() - thickness, thickness, color);
            case 3 -> sink.drawLine(block.x(), block.y(), block.x(), block.y() + block.height(), thickness, color);
            default -> throw new IllegalArgumentException("Unknown border side: " + side);
        }
    }

    private static int px(double value) {
        return Math.max(0, (int) Math.round(value));
    }
}
