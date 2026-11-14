package ru.mcs.spi.editor;

import java.util.Map;

/**
 * Исходный диапазон AST-узла в нулевых UTF-16 offsets. {@code end} исключается,
 * {@code depth} равен нулю у документа; attributes содержат только независимые от парсера значения.
 */
public record BlockSpan(BlockKind kind, int start, int end, int depth, Map<String, String> attributes) {

    public BlockSpan {
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (start < 0 || end < start || depth < 0) {
            throw new IllegalArgumentException("Invalid block span: " + kind + " " + start + ".." + end
                    + " at depth " + depth);
        }
        attributes = attributes == null || attributes.isEmpty() ? Map.of() : Map.copyOf(attributes);
    }

    public BlockSpan(BlockKind kind, int start, int end, int depth) {
        this(kind, start, end, depth, Map.of());
    }

    public int length() {
        return end - start;
    }
}
