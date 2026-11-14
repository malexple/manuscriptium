package ru.mcs.plugin.mdeditor.action;

import ru.mcs.spi.editor.BlockKind;

/** Неизменяемый контекст ПКМ; не содержит ссылок на живой AST или компонент после закрытия меню. */
public record EditorContext(BlockKind blockKind, boolean hasSelection, boolean link, boolean image) {

    public EditorContext {
        blockKind = blockKind == null ? BlockKind.PARAGRAPH : blockKind;
    }

    public static EditorContext paragraph() {
        return new EditorContext(BlockKind.PARAGRAPH, false, false, false);
    }

    public boolean code() {
        return blockKind == BlockKind.FENCED_CODE || blockKind == BlockKind.INDENTED_CODE;
    }

    public boolean table() {
        return blockKind == BlockKind.TABLE;
    }

    public boolean math() {
        return blockKind == BlockKind.MATH_BLOCK || blockKind == BlockKind.MATH_INLINE;
    }
}
