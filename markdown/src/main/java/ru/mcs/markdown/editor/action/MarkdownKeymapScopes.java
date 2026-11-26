package ru.mcs.markdown.editor.action;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;

/** Области сочетаний Markdown: визуальный Canvas (профиль Typora) и вкладка «Исходник» (без табличных команд). */
public final class MarkdownKeymapScopes {

    public static final String LANGUAGE_ID = "markdown";
    public static final String CANVAS = "markdown.keys.canvas";
    public static final String SOURCE = "markdown.keys.source";

    private MarkdownKeymapScopes() {
    }

    public static void register(KeymapService keymap, UnaryOperator<String> text) {
        keymap.registerScope(LANGUAGE_ID, scope(CANVAS, text));
        keymap.registerScope(LANGUAGE_ID, scope(SOURCE, text));
    }

    public static ShortcutScope scope(String scopeId, UnaryOperator<String> text) {
        String title = text.apply(CANVAS.equals(scopeId) ? "keys.scope.canvas" : "keys.scope.source");
        List<ShortcutBinding> named = new ArrayList<>();
        for (ShortcutBinding binding : defaults(scopeId)) {
            named.add(new ShortcutBinding(binding.actionId(), name(text, binding.actionId()),
                    binding.defaultShortcut()));
        }
        return new ShortcutScope(scopeId, title, named);
    }

    /** Сочетания по умолчанию; названия здесь — идентификаторы действий. */
    public static List<ShortcutBinding> defaults(String scopeId) {
        List<ShortcutBinding> all = new ArrayList<>();
        add(all, EditorActionIds.TOGGLE_MODE, "shortcut SLASH");
        add(all, EditorActionIds.FOCUS_MODE, "F8");
        add(all, EditorActionIds.TYPEWRITER_MODE, "F9");
        add(all, EditorActionIds.AUTO_PAIR, "shortcut shift P");
        add(all, EditorActionIds.WORD_COUNT, "shortcut shift W");

        add(all, EditorActionIds.UNDO, "shortcut Z");
        add(all, EditorActionIds.REDO, "shortcut shift Z");
        add(all, EditorActionIds.CUT, "shortcut X");
        add(all, EditorActionIds.COPY, "shortcut C");
        add(all, EditorActionIds.PASTE, "shortcut V");
        add(all, EditorActionIds.PASTE_PLAIN, "shortcut shift V");
        add(all, EditorActionIds.SELECT_ALL, "shortcut A");
        add(all, EditorActionIds.DELETE_BLOCK, "shortcut shift D");
        add(all, EditorActionIds.TOGGLE_CASE, "shortcut U");
        add(all, EditorActionIds.DUPLICATE_LINE, "shortcut D");
        add(all, EditorActionIds.DELETE_LINE, "shortcut Y");
        add(all, EditorActionIds.EXTEND_SELECTION, "shortcut W");

        add(all, EditorActionIds.PARAGRAPH, "shortcut 0");
        for (int level = 1; level <= 6; level++) {
            add(all, EditorActionIds.heading(level), "shortcut " + level);
        }
        add(all, EditorActionIds.BOLD, "shortcut B");
        add(all, EditorActionIds.ITALIC, "shortcut I");
        add(all, EditorActionIds.UNDERLINE, "shortcut shift U");
        add(all, EditorActionIds.STRIKETHROUGH, "alt shift 5");
        add(all, EditorActionIds.INLINE_CODE, "shortcut shift BACK_QUOTE");
        add(all, EditorActionIds.LINK, "shortcut K");
        add(all, EditorActionIds.CLEAR_FORMATTING, "shortcut BACK_SLASH");

        add(all, EditorActionIds.BLOCK_QUOTE, "shortcut shift Q");
        add(all, EditorActionIds.BULLET_LIST, "shortcut shift CLOSE_BRACKET");
        add(all, EditorActionIds.ORDERED_LIST, "shortcut shift OPEN_BRACKET");
        add(all, EditorActionIds.TASK_LIST, "shortcut shift X");
        add(all, EditorActionIds.CODE_BLOCK, "shortcut shift K");
        add(all, EditorActionIds.TABLE, "shortcut T");
        add(all, EditorActionIds.MATH_BLOCK, "shortcut shift M");
        add(all, EditorActionIds.HORIZONTAL_RULE, "shortcut shift H");
        add(all, EditorActionIds.IMAGE, "shortcut shift I");

        add(all, EditorActionIds.TABLE_ADD_ROW, "shortcut alt DOWN");
        add(all, EditorActionIds.TABLE_REMOVE_ROW, "shortcut alt UP");
        add(all, EditorActionIds.TABLE_ADD_COLUMN, "shortcut alt RIGHT");
        add(all, EditorActionIds.TABLE_REMOVE_COLUMN, "shortcut alt LEFT");
        add(all, EditorActionIds.TABLE_ALIGN_LEFT, "shortcut alt L");
        add(all, EditorActionIds.TABLE_ALIGN_CENTER, "shortcut alt C");
        add(all, EditorActionIds.TABLE_ALIGN_RIGHT, "shortcut alt R");
        add(all, EditorActionIds.TABLE_COPY_CELL, "shortcut alt shift C");
        add(all, EditorActionIds.TABLE_CLEAR_CELL, "shortcut alt BACK_SPACE");

        if (CANVAS.equals(scopeId)) {
            return all;
        }
        return all.stream().filter(binding -> !isTableAction(binding.actionId())).toList();
    }

    public static boolean isTableAction(String actionId) {
        return actionId.startsWith(EditorActionIds.PREFIX + "table") && !EditorActionIds.TABLE.equals(actionId);
    }

    private static void add(List<ShortcutBinding> target, String actionId, String shortcut) {
        target.add(new ShortcutBinding(actionId, actionId, shortcut));
    }

    private static String name(UnaryOperator<String> text, String actionId) {
        String shortId = actionId.substring(EditorActionIds.PREFIX.length());
        for (String key : new String[] {"action." + shortId, "action." + shortId + ".enable"}) {
            String value = text.apply(key);
            if (value != null && !value.isBlank() && !value.equals(key)) {
                return value;
            }
        }
        return shortId;
    }
}
