package ru.mcs.markdown.editor.action;

import ru.mcs.markdown.api.BlockKind;

/** Неизменяемый контекст ПКМ; не содержит ссылок на живой AST или компонент после закрытия меню. */
public record EditorBlockContext(BlockKind blockKind, boolean hasSelection, boolean link, boolean image) {

    public EditorBlockContext {
        blockKind = blockKind == null ? BlockKind.PARAGRAPH : blockKind;
    }

    public static EditorBlockContext paragraph() {
        return new EditorBlockContext(BlockKind.PARAGRAPH, false, false, false);
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
