package ru.mcs.editor.view;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;

/**
 * Настраиваемые общие клавиши редактора (область «Общие»). Заводские сочетания ставит {@link KeyBindingResolver};
 * {@link #apply} переносит привязку, только когда пользователь выбрал другое сочетание, и не трогает
 * привязки других подсистем на том же компоненте.
 */
public final class CommonKeymap {

    public static final String LANGUAGE_ID = "editor";
    public static final String SCOPE = "editor.keys.common";

    private static final String APPLIED = "mcs.commonKeymap.applied";

    private record Command(String id, String defaultShortcut, String legacyName,
                           Function<InputController, Runnable> action) {

        String actionName() {
            return "editor.common." + id;
        }
    }

    private CommonKeymap() {
    }

    /** Регистрирует область; название и имена действий берутся из ключей {@code keys.scope.common}, {@code keys.common.<id>}. */
    public static void register(KeymapService keymap, UnaryOperator<String> text) {
        keymap.registerScope(LANGUAGE_ID, scope(text));
    }

    public static ShortcutScope scope(UnaryOperator<String> text) {
        List<ShortcutBinding> bindings = new ArrayList<>();
        for (Command command : commands()) {
            bindings.add(new ShortcutBinding(command.id(), name(text, "keys.common." + command.id(), command.id()),
                    command.defaultShortcut()));
        }
        return new ShortcutScope(SCOPE, name(text, "keys.scope.common", "General"), bindings);
    }

    /** Приводит привязки компонента в соответствие с сервисом; без зарегистрированной области ничего не делает. */
    public static void apply(EditorCanvas canvas, KeymapService keymap) {
        if (keymap.scopes(LANGUAGE_ID).stream().noneMatch(scope -> scope.id().equals(SCOPE))) {
            return;
        }
        int mask = Shortcuts.defaultMask();
        @SuppressWarnings("unchecked")
        Map<String, KeyStroke> applied = (Map<String, KeyStroke>) canvas.getClientProperty(APPLIED);
        if (applied == null) {
            applied = new HashMap<>();
            canvas.putClientProperty(APPLIED, applied);
        }
        InputMap inputs = canvas.getInputMap(JComponent.WHEN_FOCUSED);
        for (Command command : commands()) {
            KeyStroke previous = applied.containsKey(command.id()) ? applied.get(command.id())
                    : parse(command.defaultShortcut(), mask);
            KeyStroke next = parse(keymap.shortcut(SCOPE, command.id()), mask);
            if (Objects.equals(previous, next)) {
                continue;
            }
            if (previous != null) {
                Object bound = inputs.get(previous);
                if (command.legacyName().equals(bound) || command.actionName().equals(bound)) {
                    inputs.remove(previous);
                }
            }
            if (next != null) {
                Runnable run = command.action().apply(canvas.input());
                inputs.put(next, command.actionName());
                canvas.getActionMap().put(command.actionName(), new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        run.run();
                    }
                });
            }
            applied.put(command.id(), next);
        }
    }

    private static List<Command> commands() {
        String word = isMacOs() ? "alt" : "shortcut";
        return List.of(
                new Command("undo", "shortcut Z", "undo", input -> input::undo),
                new Command("redo", "shortcut shift Z", "redo", input -> input::redo),
                new Command("redoAlt", "shortcut Y", "redo", input -> input::redo),
                new Command("cut", "shortcut X", "cut", input -> input::cut),
                new Command("cutAlt", "shift DELETE", "cutShiftDelete", input -> input::cut),
                new Command("copy", "shortcut C", "copy", input -> input::copy),
                new Command("copyAlt", "ctrl INSERT", "copyCtrlInsert", input -> input::copy),
                new Command("paste", "shortcut V", "paste", input -> input::paste),
                new Command("pasteAlt", "shift INSERT", "pasteShiftInsert", input -> input::paste),
                new Command("selectAll", "shortcut A", "selectAll", input -> input::selectAll),
                new Command("deletePreviousWord", word + " BACK_SPACE", "deletePreviousWord",
                        input -> input::deletePreviousWord),
                new Command("deleteNextWord", word + " DELETE", "deleteNextWord", input -> input::deleteNextWord));
    }

    private static KeyStroke parse(String shortcut, int mask) {
        try {
            return Shortcuts.parse(shortcut, mask);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String name(UnaryOperator<String> text, String key, String fallback) {
        String value = text.apply(key);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
