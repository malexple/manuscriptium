package ru.mcs.plugin.mdeditor.action;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.ActionPlaces;

/** Профиль Typora: локальные клавиши редактора делегируют в общий ActionManager. */
public final class TyporaKeymap {

    private final ActionManager actions;
    private final int shortcutMask;

    public TyporaKeymap(ActionManager actions) {
        this(actions, defaultShortcutMask());
    }

    TyporaKeymap(ActionManager actions, int shortcutMask) {
        this.actions = actions;
        this.shortcutMask = shortcutMask;
    }

    public void install(EditorContext canvas) {
        bind(canvas, KeyEvent.VK_SLASH, shortcut(), EditorActionIds.TOGGLE_MODE);
        bind(canvas, KeyEvent.VK_F8, 0, EditorActionIds.FOCUS_MODE);
        bind(canvas, KeyEvent.VK_F9, 0, EditorActionIds.TYPEWRITER_MODE);
        bind(canvas, KeyEvent.VK_P, shortcutShift(), EditorActionIds.AUTO_PAIR);
        bind(canvas, KeyEvent.VK_W, shortcutShift(), EditorActionIds.WORD_COUNT);

        bind(canvas, KeyEvent.VK_Z, shortcut(), EditorActionIds.UNDO);
        bind(canvas, KeyEvent.VK_Z, shortcutShift(), EditorActionIds.REDO);
        bind(canvas, KeyEvent.VK_X, shortcut(), EditorActionIds.CUT);
        bind(canvas, KeyEvent.VK_C, shortcut(), EditorActionIds.COPY);
        bind(canvas, KeyEvent.VK_V, shortcut(), EditorActionIds.PASTE);
        bind(canvas, KeyEvent.VK_A, shortcut(), EditorActionIds.SELECT_ALL);
        bind(canvas, KeyEvent.VK_D, shortcutShift(), EditorActionIds.DELETE_BLOCK);
        bind(canvas, KeyEvent.VK_U, shortcut(), EditorActionIds.TOGGLE_CASE);
        bind(canvas, KeyEvent.VK_D, shortcut(), EditorActionIds.DUPLICATE_LINE);
        bind(canvas, KeyEvent.VK_Y, shortcut(), EditorActionIds.DELETE_LINE);
        bind(canvas, KeyEvent.VK_W, shortcut(), EditorActionIds.EXTEND_SELECTION);

        bind(canvas, KeyEvent.VK_0, shortcut(), EditorActionIds.PARAGRAPH);
        for (int level = 1; level <= 6; level++) {
            bind(canvas, KeyEvent.VK_0 + level, shortcut(), EditorActionIds.heading(level));
        }
        bind(canvas, KeyEvent.VK_B, shortcut(), EditorActionIds.BOLD);
        bind(canvas, KeyEvent.VK_I, shortcut(), EditorActionIds.ITALIC);
        bind(canvas, KeyEvent.VK_U, shortcutShift(), EditorActionIds.UNDERLINE);
        bind(canvas, KeyEvent.VK_5, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                EditorActionIds.STRIKETHROUGH);
        bind(canvas, KeyEvent.VK_BACK_QUOTE, shortcutShift(), EditorActionIds.INLINE_CODE);
        bind(canvas, KeyEvent.VK_K, shortcut(), EditorActionIds.LINK);
        bind(canvas, KeyEvent.VK_BACK_SLASH, shortcut(), EditorActionIds.CLEAR_FORMATTING);

        bind(canvas, KeyEvent.VK_Q, shortcutShift(), EditorActionIds.BLOCK_QUOTE);
        bind(canvas, KeyEvent.VK_CLOSE_BRACKET, shortcutShift(), EditorActionIds.BULLET_LIST);
        bind(canvas, KeyEvent.VK_OPEN_BRACKET, shortcutShift(), EditorActionIds.ORDERED_LIST);
        bind(canvas, KeyEvent.VK_X, shortcutShift(), EditorActionIds.TASK_LIST);
        bind(canvas, KeyEvent.VK_K, shortcutShift(), EditorActionIds.CODE_BLOCK);
        bind(canvas, KeyEvent.VK_T, shortcut(), EditorActionIds.TABLE);
        bind(canvas, KeyEvent.VK_M, shortcutShift(), EditorActionIds.MATH_BLOCK);
        bind(canvas, KeyEvent.VK_H, shortcutShift(), EditorActionIds.HORIZONTAL_RULE);
        bind(canvas, KeyEvent.VK_I, shortcutShift(), EditorActionIds.IMAGE);
        int tableMask = shortcut() | InputEvent.ALT_DOWN_MASK;
        bind(canvas, KeyEvent.VK_DOWN, tableMask, EditorActionIds.TABLE_ADD_ROW);
        bind(canvas, KeyEvent.VK_UP, tableMask, EditorActionIds.TABLE_REMOVE_ROW);
        bind(canvas, KeyEvent.VK_RIGHT, tableMask, EditorActionIds.TABLE_ADD_COLUMN);
        bind(canvas, KeyEvent.VK_LEFT, tableMask, EditorActionIds.TABLE_REMOVE_COLUMN);
        bind(canvas, KeyEvent.VK_L, tableMask, EditorActionIds.TABLE_ALIGN_LEFT);
        bind(canvas, KeyEvent.VK_C, tableMask, EditorActionIds.TABLE_ALIGN_CENTER);
        bind(canvas, KeyEvent.VK_R, tableMask, EditorActionIds.TABLE_ALIGN_RIGHT);
        bind(canvas, KeyEvent.VK_C, tableMask | InputEvent.SHIFT_DOWN_MASK, EditorActionIds.TABLE_COPY_CELL);
        bind(canvas, KeyEvent.VK_BACK_SPACE, tableMask, EditorActionIds.TABLE_CLEAR_CELL);
    }

    private int shortcut() {
        return shortcutMask;
    }

    private int shortcutShift() {
        return shortcutMask | InputEvent.SHIFT_DOWN_MASK;
    }

    private void bind(EditorContext context, int keyCode, int modifiers, String actionId) {
        KeyStroke key = KeyStroke.getKeyStroke(keyCode, modifiers);
        JComponent component = context.component();
        component.getInputMap(JComponent.WHEN_FOCUSED).put(key, actionId);
        component.getActionMap().put(actionId, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                EditorActionDispatcher.perform(actions, actionId, ActionPlaces.MAIN_MENU);
            }
        });
    }

    private static int defaultShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException e) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }
}
