package ru.mcs.markdown.layout;

import java.util.List;

/** Неизменяемый результат раскладки одной версии AST/стиля/ширины. */
public record LayoutResult(long documentVersion, int viewportWidth, int writeX, int writeWidth,
                           int height, List<BlockBox> blocks) {

    public LayoutResult {
        blocks = List.copyOf(blocks);
    }

    public BlockBox blockAtY(int y) {
        if (blocks.isEmpty()) {
            return null;
        }
        BlockBox nearest = blocks.getFirst();
        for (BlockBox block : blocks) {
            if (y >= block.y() && y < block.y() + block.height()) {
                return block;
            }
            if (block.y() <= y) {
                nearest = block;
            } else {
                break;
            }
        }
        return y < 0 ? blocks.getFirst() : nearest;
    }
}
