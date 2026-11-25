package ru.mcs.editor.view;

import java.util.List;

/**
 * Разбор текста документа на токены подсветки. Реализация языка или формата передаётся в
 * {@link EditorCanvas#setSyntaxHighlighting(SyntaxHighlighter, TokenColors)}.
 * Метод вызывается после каждого изменения документа на EDT при рисовании, поэтому должен быть быстрым
 * и не должен изменять состояние.
 */
public interface SyntaxHighlighter {

    /**
     * @return участки, отсортированные по началу и не пересекающиеся; всё, что не покрыто, рисуется основным цветом
     */
    List<HighlightSpan> highlight(CharSequence text);
}
