package ru.mcs.plugin.mdeditor.view;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Централизованный keymap: menu shortcut ОС для undo/clipboard, Option для word navigation на macOS. */
public final class KeyBindingResolver {

    private final int shortcutMask;
    private final int wordMask;

    public KeyBindingResolver() {
        this(defaultShortcutMask(), isMacOs());
    }

    KeyBindingResolver(int shortcutMask, boolean macOs) {
        this.shortcutMask = shortcutMask;
        wordMask = macOs ? InputEvent.ALT_DOWN_MASK : shortcutMask;
    }

    public void install(EditorCanvas canvas, InputController input) {
        bindMovement(canvas, KeyEvent.VK_LEFT, () -> input.moveLeft(false, false),
                () -> input.moveLeft(true, false), () -> input.moveLeft(false, true), () -> input.moveLeft(true, true));
        bindMovement(canvas, KeyEvent.VK_RIGHT, () -> input.moveRight(false, false),
                () -> input.moveRight(true, false), () -> input.moveRight(false, true), () -> input.moveRight(true, true));
        bind(canvas, key(KeyEvent.VK_UP, 0), "up", () -> input.moveVertical(-1, false));
        bind(canvas, key(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK), "selectUp", () -> input.moveVertical(-1, true));
        bind(canvas, key(KeyEvent.VK_DOWN, 0), "down", () -> input.moveVertical(1, false));
        bind(canvas, key(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK), "selectDown", () -> input.moveVertical(1, true));
        bind(canvas, key(KeyEvent.VK_HOME, 0), "home", () -> input.moveHome(false, false));
        bind(canvas, key(KeyEvent.VK_HOME, InputEvent.SHIFT_DOWN_MASK), "selectHome", () -> input.moveHome(true, false));
        bind(canvas, key(KeyEvent.VK_HOME, shortcutMask), "documentHome", () -> input.moveHome(false, true));
        bind(canvas, key(KeyEvent.VK_HOME, shortcutMask | InputEvent.SHIFT_DOWN_MASK), "selectDocumentHome",
                () -> input.moveHome(true, true));
        bind(canvas, key(KeyEvent.VK_END, 0), "end", () -> input.moveEnd(false, false));
        bind(canvas, key(KeyEvent.VK_END, InputEvent.SHIFT_DOWN_MASK), "selectEnd", () -> input.moveEnd(true, false));
        bind(canvas, key(KeyEvent.VK_END, shortcutMask), "documentEnd", () -> input.moveEnd(false, true));
        bind(canvas, key(KeyEvent.VK_END, shortcutMask | InputEvent.SHIFT_DOWN_MASK), "selectDocumentEnd",
                () -> input.moveEnd(true, true));
        bind(canvas, key(KeyEvent.VK_PAGE_UP, 0), "pageUp", () -> input.movePage(-1, false));
        bind(canvas, key(KeyEvent.VK_PAGE_UP, InputEvent.SHIFT_DOWN_MASK), "selectPageUp", () -> input.movePage(-1, true));
        bind(canvas, key(KeyEvent.VK_PAGE_DOWN, 0), "pageDown", () -> input.movePage(1, false));
        bind(canvas, key(KeyEvent.VK_PAGE_DOWN, InputEvent.SHIFT_DOWN_MASK), "selectPageDown", () -> input.movePage(1, true));
        bind(canvas, key(KeyEvent.VK_BACK_SPACE, 0), "backspace", input::deleteBackward);
        bind(canvas, key(KeyEvent.VK_BACK_SPACE, wordMask), "deletePreviousWord", input::deletePreviousWord);
        bind(canvas, key(KeyEvent.VK_DELETE, 0), "delete", input::deleteForward);
        bind(canvas, key(KeyEvent.VK_DELETE, wordMask), "deleteNextWord", input::deleteNextWord);
        bind(canvas, key(KeyEvent.VK_ENTER, 0), "newline", () -> input.insertText("\n"));
        bind(canvas, key(KeyEvent.VK_TAB, 0), "tab", () -> input.insertText("\t"));
        bind(canvas, key(KeyEvent.VK_A, shortcutMask), "selectAll", input::selectAll);
        bind(canvas, key(KeyEvent.VK_C, shortcutMask), "copy", input::copy);
        bind(canvas, key(KeyEvent.VK_X, shortcutMask), "cut", input::cut);
        bind(canvas, key(KeyEvent.VK_V, shortcutMask), "paste", input::paste);
        bind(canvas, key(KeyEvent.VK_Z, shortcutMask), "undo", input::undo);
        bind(canvas, key(KeyEvent.VK_Z, shortcutMask | InputEvent.SHIFT_DOWN_MASK), "redo", input::redo);
        bind(canvas, key(KeyEvent.VK_Y, shortcutMask), "redoY", input::redo);
    }

    int shortcutMask() {
        return shortcutMask;
    }

    int wordMask() {
        return wordMask;
    }

    private void bindMovement(EditorCanvas canvas, int key, Runnable plain, Runnable shift,
                              Runnable word, Runnable wordShift) {
        bind(canvas, key(key, 0), KeyEvent.getKeyText(key), plain);
        bind(canvas, key(key, InputEvent.SHIFT_DOWN_MASK), KeyEvent.getKeyText(key) + "Select", shift);
        bind(canvas, key(key, wordMask), KeyEvent.getKeyText(key) + "Word", word);
        bind(canvas, key(key, wordMask | InputEvent.SHIFT_DOWN_MASK), KeyEvent.getKeyText(key) + "WordSelect", wordShift);
    }

    private static void bind(EditorCanvas canvas, KeyStroke key, String name, Runnable action) {
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        Action previous = canvas.getActionMap().get(name);
        if (previous == null) {
            canvas.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent event) {
                    action.run();
                }
            });
        }
    }

    private static KeyStroke key(int keyCode, int modifiers) {
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }

    private static int defaultShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException e) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }
}
