package ru.mcs.markdown.editor.action;

import java.util.List;

/** Единые ID для меню, popup, палитры команд и Typora-keymap. */
public final class EditorActionIds {

    public static final String PREFIX = "markdown.editor.";
    public static final String PARAGRAPH_MENU = "MarkdownEditor.ParagraphMenu";
    public static final String FORMAT_MENU = "MarkdownEditor.FormatMenu";
    public static final String INSERT_MENU = "MarkdownEditor.InsertMenu";

    public static final String TOGGLE_MODE = PREFIX + "toggleMode";
    public static final String FOCUS_MODE = PREFIX + "focusMode";
    public static final String TYPEWRITER_MODE = PREFIX + "typewriterMode";
    public static final String AUTO_PAIR = PREFIX + "autoPair";
    public static final String WORD_COUNT = PREFIX + "wordCount";

    public static final String UNDO = PREFIX + "undo";
    public static final String REDO = PREFIX + "redo";
    public static final String CUT = PREFIX + "cut";
    public static final String COPY = PREFIX + "copy";
    public static final String PASTE = PREFIX + "paste";
    public static final String SELECT_ALL = PREFIX + "selectAll";
    public static final String DELETE_BLOCK = PREFIX + "deleteBlock";
    public static final String TOGGLE_CASE = PREFIX + "toggleCase";
    public static final String DUPLICATE_LINE = PREFIX + "duplicateLine";
    public static final String DELETE_LINE = PREFIX + "deleteLine";
    public static final String EXTEND_SELECTION = PREFIX + "extendSelection";

    public static final String PARAGRAPH = PREFIX + "paragraph";
    public static final String BOLD = PREFIX + "bold";
    public static final String ITALIC = PREFIX + "italic";
    public static final String UNDERLINE = PREFIX + "underline";
    public static final String STRIKETHROUGH = PREFIX + "strikethrough";
    public static final String INLINE_CODE = PREFIX + "inlineCode";
    public static final String LINK = PREFIX + "link";
    public static final String CLEAR_FORMATTING = PREFIX + "clearFormatting";

    public static final String BLOCK_QUOTE = PREFIX + "blockQuote";
    public static final String BULLET_LIST = PREFIX + "bulletList";
    public static final String ORDERED_LIST = PREFIX + "orderedList";
    public static final String TASK_LIST = PREFIX + "taskList";
    public static final String CODE_BLOCK = PREFIX + "codeBlock";
    public static final String TABLE = PREFIX + "table";
    public static final String MATH_BLOCK = PREFIX + "mathBlock";
    public static final String HORIZONTAL_RULE = PREFIX + "horizontalRule";
    public static final String IMAGE = PREFIX + "image";
    public static final String TABLE_ADD_ROW = PREFIX + "tableAddRow";
    public static final String TABLE_REMOVE_ROW = PREFIX + "tableRemoveRow";
    public static final String TABLE_ADD_COLUMN = PREFIX + "tableAddColumn";
    public static final String TABLE_REMOVE_COLUMN = PREFIX + "tableRemoveColumn";
    public static final String TABLE_ALIGN_LEFT = PREFIX + "tableAlignLeft";
    public static final String TABLE_ALIGN_CENTER = PREFIX + "tableAlignCenter";
    public static final String TABLE_ALIGN_RIGHT = PREFIX + "tableAlignRight";
    public static final String TABLE_COPY_CELL = PREFIX + "tableCopyCell";
    public static final String TABLE_CLEAR_CELL = PREFIX + "tableClearCell";

    public static final List<String> ALL = List.of(
            TOGGLE_MODE, FOCUS_MODE, TYPEWRITER_MODE, AUTO_PAIR, WORD_COUNT,
            UNDO, REDO, CUT, COPY, PASTE, SELECT_ALL, DELETE_BLOCK,
            TOGGLE_CASE, DUPLICATE_LINE, DELETE_LINE, EXTEND_SELECTION,
            PARAGRAPH, heading(1), heading(2), heading(3), heading(4), heading(5), heading(6),
            BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, INLINE_CODE, LINK, CLEAR_FORMATTING,
            BLOCK_QUOTE, BULLET_LIST, ORDERED_LIST, TASK_LIST, CODE_BLOCK, TABLE, MATH_BLOCK,
            HORIZONTAL_RULE, IMAGE,
            TABLE_ADD_ROW, TABLE_REMOVE_ROW, TABLE_ADD_COLUMN, TABLE_REMOVE_COLUMN,
            TABLE_ALIGN_LEFT, TABLE_ALIGN_CENTER, TABLE_ALIGN_RIGHT, TABLE_COPY_CELL, TABLE_CLEAR_CELL);

    private EditorActionIds() {
    }

    public static String heading(int level) {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("Heading level must be 1..6: " + level);
        }
        return PREFIX + "heading" + level;
    }
}
