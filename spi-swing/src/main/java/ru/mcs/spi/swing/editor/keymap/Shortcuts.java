package ru.mcs.spi.swing.editor.keymap;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.KeyStroke;

/**
 * Текстовая запись сочетаний: модификаторы {@code shortcut ctrl alt shift meta} и имя клавиши
 * (буква, цифра или имя после {@code VK_}: {@code SLASH}, {@code F8}, {@code BACK_SPACE}). Слово {@code shortcut}
 * означает основную клавишу меню платформы: Ctrl в Windows и Linux, Cmd в macOS.
 */
public final class Shortcuts {

    private static final Map<Integer, String> NAMES = keyNames();
    private static final int PHYSICAL = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    private Shortcuts() {
    }

    public static int defaultMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException headless) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    /** Разбирает запись; пустая строка даёт null, неверная запись — {@link IllegalArgumentException}. */
    public static KeyStroke parse(String text, int shortcutMask) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int modifiers = 0;
        String key = null;
        for (String token : text.trim().split("\\s+")) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "shortcut" -> modifiers |= shortcutMask;
                case "ctrl", "control" -> modifiers |= InputEvent.CTRL_DOWN_MASK;
                case "alt", "option" -> modifiers |= InputEvent.ALT_DOWN_MASK;
                case "shift" -> modifiers |= InputEvent.SHIFT_DOWN_MASK;
                case "meta", "cmd" -> modifiers |= InputEvent.META_DOWN_MASK;
                default -> {
                    if (key != null) {
                        throw new IllegalArgumentException("Invalid shortcut: " + text);
                    }
                    key = token;
                }
            }
        }
        if (key == null) {
            throw new IllegalArgumentException("Shortcut has no key: " + text);
        }
        return KeyStroke.getKeyStroke(keyCode(key, text), modifiers);
    }

    /** Записывает сочетание в переносимом виде: основной модификатор платформы становится {@code shortcut}. */
    public static String format(KeyStroke stroke, int shortcutMask) {
        int modifiers = stroke.getModifiers() & PHYSICAL;
        List<String> parts = new ArrayList<>();
        if (shortcutMask != 0 && (modifiers & shortcutMask) == shortcutMask) {
            parts.add("shortcut");
            modifiers &= ~shortcutMask;
        }
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            parts.add("ctrl");
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            parts.add("alt");
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            parts.add("shift");
        }
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
            parts.add("meta");
        }
        parts.add(keyName(stroke.getKeyCode()));
        return String.join(" ", parts);
    }

    /** Текст для пользователя, например {@code Ctrl+Alt+L}. */
    public static String display(KeyStroke stroke) {
        if (stroke == null) {
            return "";
        }
        String modifiers = InputEvent.getModifiersExText(stroke.getModifiers() & PHYSICAL);
        String key = KeyEvent.getKeyText(stroke.getKeyCode());
        return modifiers.isEmpty() ? key : modifiers + "+" + key;
    }

    private static int keyCode(String key, String whole) {
        if (key.length() == 1) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(key.charAt(0)));
            if (code != KeyEvent.VK_UNDEFINED) {
                return code;
            }
        } else {
            try {
                return KeyEvent.class.getField("VK_" + key.toUpperCase(Locale.ROOT)).getInt(null);
            } catch (ReflectiveOperationException unknown) {
                // сообщение ниже
            }
        }
        throw new IllegalArgumentException("Unknown key '" + key + "' in shortcut: " + whole);
    }

    private static String keyName(int code) {
        if ((code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) || (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z)) {
            return String.valueOf((char) code);
        }
        String name = NAMES.get(code);
        if (name == null) {
            throw new IllegalArgumentException("Unsupported key code: " + code);
        }
        return name;
    }

    private static Map<Integer, String> keyNames() {
        Map<Integer, String> names = new HashMap<>();
        Field[] fields = KeyEvent.class.getFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        for (Field field : fields) {
            if (field.getName().startsWith("VK_") && field.getType() == int.class
                    && Modifier.isStatic(field.getModifiers())) {
                try {
                    names.putIfAbsent(field.getInt(null), field.getName().substring(3));
                } catch (IllegalAccessException ignored) {
                    // публичные константы всегда доступны
                }
            }
        }
        return Map.copyOf(names);
    }
}
