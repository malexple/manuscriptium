package ru.mcs.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;

/** Область сочетаний YAML-редактора; общие клавиши (отмена, буфер обмена) приходят из области «Общие». */
final class YamlKeymapScopes {

    static final String LANGUAGE_ID = "yaml";
    static final String SCOPE = "yaml.keys.editor";
    static final String TOGGLE_COMMENT = "toggleComment";
    static final String REFORMAT = "reformat";
    static final String VALIDATE = "validate";

    private YamlKeymapScopes() {
    }

    static void register(KeymapService keymap, UnaryOperator<String> text) {
        keymap.registerScope(LANGUAGE_ID, scope(text));
    }

    static ShortcutScope scope(UnaryOperator<String> text) {
        List<ShortcutBinding> named = new ArrayList<>();
        for (ShortcutBinding binding : defaults()) {
            named.add(new ShortcutBinding(binding.actionId(), name(text, binding.actionId()),
                    binding.defaultShortcut()));
        }
        String title = text.apply("keys.scope.editor");
        return new ShortcutScope(SCOPE, title == null || title.equals("keys.scope.editor") ? "Editor" : title, named);
    }

    static List<ShortcutBinding> defaults() {
        return List.of(
                new ShortcutBinding(TOGGLE_COMMENT, TOGGLE_COMMENT, "shortcut SLASH"),
                new ShortcutBinding(REFORMAT, REFORMAT, "shortcut alt L"),
                new ShortcutBinding(VALIDATE, VALIDATE, "shortcut alt V"));
    }

    private static String name(UnaryOperator<String> text, String actionId) {
        String key = "keys.yaml." + actionId;
        String value = text.apply(key);
        return value == null || value.isBlank() || value.equals(key) ? actionId : value;
    }
}
