package ru.mcs.editor.view;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Set;
import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import ru.mcs.editor.action.EditorMetadataProvider;
import ru.mcs.metadata.MetadataCatalog;
import ru.mcs.metadata.MetadataDocument;
import ru.mcs.metadata.TargetContext;

/** Централизованный keymap: menu shortcut ОС для undo/clipboard, Option для word navigation на macOS. */
public final class KeyBindingResolver {

    private static final MetadataDocument METADATA = MetadataCatalog.from(new EditorMetadataProvider())
            .get("editor").document();
    private static final TargetContext DESKTOP = new TargetContext("desktop", Set.of("swing"));

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
        // Tab принадлежит редактору (ячейки таблицы, отступ); смена фокуса остаётся на Ctrl+Tab.
        canvas.setFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                java.util.Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)));
        canvas.setFocusTraversalKeys(java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                java.util.Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
        bindMovement(canvas, KeyEvent.VK_LEFT, () -> {
            if (!canvas.tableStep(-1)) {
                input.moveLeft(false, false);
            }
        },
                () -> input.moveLeft(true, false), () -> input.moveLeft(false, true), () -> input.moveLeft(true, true));
        bindMovement(canvas, KeyEvent.VK_RIGHT, () -> {
            if (!canvas.tableStep(1)) {
                input.moveRight(false, false);
            }
        },
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
        bind(canvas, key(KeyEvent.VK_BACK_SPACE, 0), "backspace", () -> {
            if (!canvas.tableBoundary(-1)) {
                input.deleteBackward();
            }
        });
        bind(canvas, key(KeyEvent.VK_BACK_SPACE, wordMask), "deletePreviousWord", input::deletePreviousWord);
        bind(canvas, key(KeyEvent.VK_DELETE, 0), "delete", () -> {
            if (!canvas.tableBoundary(1)) {
                input.deleteForward();
            }
        });
        bind(canvas, key(KeyEvent.VK_DELETE, wordMask), "deleteNextWord", input::deleteNextWord);
        bind(canvas, key(KeyEvent.VK_ENTER, 0), "newline", () -> {
            if (!canvas.tableEnter()) {
                input.insertText("\n");
            }
        });
        bind(canvas, key(KeyEvent.VK_TAB, 0), "tab", () -> {
            if (!canvas.navigateTable(1)) {
                input.insertText("\t");
            }
        });
        bind(canvas, key(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), "previousTableCell",
                () -> canvas.navigateTable(-1));
        bind(canvas, key(KeyEvent.VK_A, shortcutMask), "selectAll", input::selectAll);
        bind(canvas, key(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK), "pasteShiftInsert", input::paste);
        bind(canvas, key(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK), "copyCtrlInsert", input::copy);
        bind(canvas, key(KeyEvent.VK_DELETE, InputEvent.SHIFT_DOWN_MASK), "cutShiftDelete", input::cut);
        installMetadataKeymap(canvas, input);
    }

    private void installMetadataKeymap(EditorCanvas canvas, InputController input) {
        for (MetadataDocument.KeyBinding binding : METADATA.keymap()) {
            if (!binding.applicability().matches(DESKTOP)) {
                continue;
            }
            Runnable action = switch (binding.commandId()) {
                case "editor.text.cut" -> input::cut;
                case "editor.text.copy" -> input::copy;
                case "editor.text.paste" -> input::paste;
                case "editor.text.undo" -> input::undo;
                case "editor.text.redo" -> input::redo;
                default -> throw new IllegalStateException(
                        "Editor command is not implemented: " + binding.commandId());
            };
            String name = binding.commandId().substring(binding.commandId().lastIndexOf('.') + 1);
            bind(canvas, keystroke(binding.keystroke()), name, action);
        }
    }

    private KeyStroke keystroke(String description) {
        int modifiers = 0;
        String key = null;
        for (String token : description.trim().split("\\s+")) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "shortcut" -> modifiers |= shortcutMask;
                case "shift" -> modifiers |= InputEvent.SHIFT_DOWN_MASK;
                case "ctrl", "control" -> modifiers |= InputEvent.CTRL_DOWN_MASK;
                case "alt", "option" -> modifiers |= InputEvent.ALT_DOWN_MASK;
                case "meta" -> modifiers |= InputEvent.META_DOWN_MASK;
                default -> {
                    if (key != null) {
                        throw new IllegalArgumentException("Invalid keystroke: " + description);
                    }
                    key = token;
                }
            }
        }
        if (key == null || key.length() != 1) {
            throw new IllegalArgumentException("Unsupported keystroke: " + description);
        }
        return key(KeyEvent.getExtendedKeyCodeForChar(key.charAt(0)), modifiers);
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
