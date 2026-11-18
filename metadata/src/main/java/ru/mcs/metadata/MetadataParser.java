package ru.mcs.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.mcs.metadata.MetadataDocument.Action;
import ru.mcs.metadata.MetadataDocument.Entry;
import ru.mcs.metadata.MetadataDocument.Field;
import ru.mcs.metadata.MetadataDocument.FieldType;
import ru.mcs.metadata.MetadataDocument.KeyBinding;
import ru.mcs.metadata.MetadataDocument.Menu;
import ru.mcs.metadata.MetadataDocument.Option;
import ru.mcs.metadata.MetadataDocument.Panel;
import ru.mcs.metadata.MetadataDocument.SettingsPage;
import ru.mcs.metadata.MetadataDocument.StatusWidget;
import ru.mcs.metadata.MetadataDocument.Toolbar;

/** Строго преобразует JSON в typed model; неизвестные ключи являются ошибкой формата. */
public final class MetadataParser {

    private MetadataParser() {
    }

    public static MetadataDocument parse(InputStream input) {
        try (input) {
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MetadataException("Cannot read metadata", e);
        }
    }

    public static MetadataDocument parse(String json) {
        Map<String, Object> root = object(Json.parse(json), "document", Set.of(
                "formatVersion", "actions", "menus", "toolbars", "statusBar", "settingsPages", "keymap", "panels"));
        int version = integer(root.get("formatVersion"), "formatVersion");
        return new MetadataDocument(version,
                list(root.get("actions"), "actions").stream().map(MetadataParser::action).toList(),
                list(root.get("menus"), "menus").stream().map(MetadataParser::menu).toList(),
                list(root.get("toolbars"), "toolbars").stream().map(MetadataParser::toolbar).toList(),
                list(root.get("statusBar"), "statusBar").stream().map(MetadataParser::statusWidget).toList(),
                list(root.get("settingsPages"), "settingsPages").stream().map(MetadataParser::settingsPage).toList(),
                list(root.get("keymap"), "keymap").stream().map(MetadataParser::keyBinding).toList(),
                list(root.get("panels"), "panels").stream().map(MetadataParser::panel).toList());
    }

    private static Action action(Object value) {
        Map<String, Object> object = object(value, "action", applicabilityKeys(
                "id", "textKey", "descriptionKey", "icon", "parents"));
        return new Action(requiredString(object, "id"), string(object.get("textKey"), "action.textKey"),
                string(object.get("descriptionKey"), "action.descriptionKey"),
                string(object.get("icon"), "action.icon"), strings(object.get("parents"), "action.parents"),
                applicability(object));
    }

    private static Menu menu(Object value) {
        Map<String, Object> object = object(value, "menu", applicabilityKeys(
                "id", "textKey", "popup", "parents", "children"));
        return new Menu(requiredString(object, "id"), string(object.get("textKey"), "menu.textKey"),
                bool(object.get("popup"), true), strings(object.get("parents"), "menu.parents"),
                list(object.get("children"), "menu.children").stream().map(MetadataParser::entry).toList(),
                applicability(object));
    }

    private static Toolbar toolbar(Object value) {
        Map<String, Object> object = object(value, "toolbar", applicabilityKeys("id", "children"));
        return new Toolbar(requiredString(object, "id"),
                list(object.get("children"), "toolbar.children").stream().map(MetadataParser::entry).toList(),
                applicability(object));
    }

    private static Entry entry(Object value) {
        Map<String, Object> object = object(value, "entry", Set.of("command", "separator", "position", "anchor"));
        String position = string(object.get("position"), "entry.position");
        return new Entry(string(object.get("command"), "entry.command"),
                string(object.get("separator"), "entry.separator"),
                position == null ? "last" : position,
                string(object.get("anchor"), "entry.anchor"));
    }

    private static StatusWidget statusWidget(Object value) {
        Map<String, Object> object = object(value, "statusWidget", applicabilityKeys("id", "command", "alignment"));
        String alignment = string(object.get("alignment"), "statusWidget.alignment");
        return new StatusWidget(requiredString(object, "id"), requiredString(object, "command"),
                alignment == null ? "right" : alignment, applicability(object));
    }

    private static SettingsPage settingsPage(Object value) {
        Map<String, Object> object = object(value, "settingsPage", applicabilityKeys("id", "titleKey", "fields"));
        return new SettingsPage(requiredString(object, "id"), requiredString(object, "titleKey"),
                list(object.get("fields"), "settingsPage.fields").stream().map(MetadataParser::field).toList(),
                applicability(object));
    }

    private static Field field(Object value) {
        Map<String, Object> object = object(value, "field", Set.of(
                "id", "type", "labelKey", "stateKey", "noteKey", "options"));
        String type = requiredString(object, "type").toUpperCase(Locale.ROOT);
        FieldType fieldType;
        try {
            fieldType = FieldType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new MetadataException("Unknown settings field type: " + type, e);
        }
        return new Field(requiredString(object, "id"), fieldType, requiredString(object, "labelKey"),
                requiredString(object, "stateKey"), string(object.get("noteKey"), "field.noteKey"),
                list(object.get("options"), "field.options").stream().map(MetadataParser::option).toList());
    }

    private static Option option(Object value) {
        Map<String, Object> object = object(value, "option", Set.of("value", "labelKey"));
        return new Option(requiredString(object, "value"), requiredString(object, "labelKey"));
    }

    private static KeyBinding keyBinding(Object value) {
        Map<String, Object> object = object(value, "keyBinding", applicabilityKeys("command", "keystroke"));
        return new KeyBinding(requiredString(object, "command"), requiredString(object, "keystroke"),
                applicability(object));
    }

    private static Panel panel(Object value) {
        Map<String, Object> object = object(value, "panel", applicabilityKeys(
                "id", "titleKey", "command", "anchor", "icon"));
        return new Panel(requiredString(object, "id"), requiredString(object, "titleKey"),
                requiredString(object, "command"), string(object.get("anchor"), "panel.anchor"),
                string(object.get("icon"), "panel.icon"), applicability(object));
    }

    private static TargetRule applicability(Map<String, Object> object) {
        return new TargetRule(strings(object.get("targets"), "targets"),
                new LinkedHashSet<>(strings(object.get("requiresCapabilities"), "requiresCapabilities")),
                new LinkedHashSet<>(strings(object.get("excludesCapabilities"), "excludesCapabilities")));
    }

    private static Set<String> applicabilityKeys(String... keys) {
        Set<String> result = new LinkedHashSet<>(List.of(keys));
        result.addAll(List.of("targets", "requiresCapabilities", "excludesCapabilities"));
        return Set.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String path, Set<String> allowedKeys) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new MetadataException(path + " must be an object");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String name) || !allowedKeys.contains(name)) {
                throw new MetadataException("Unknown key " + key + " in " + path);
            }
        }
        return (Map<String, Object>) map;
    }

    private static List<Object> list(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new MetadataException(path + " must be an array");
        }
        return new java.util.ArrayList<>(list);
    }

    private static List<String> strings(Object value, String path) {
        return list(value, path).stream().map(item -> {
            if (!(item instanceof String string) || string.isBlank()) {
                throw new MetadataException(path + " must contain only non-blank strings");
            }
            return string;
        }).toList();
    }

    private static String requiredString(Map<String, Object> object, String key) {
        String value = string(object.get(key), key);
        if (value == null || value.isBlank()) {
            throw new MetadataException(key + " must be a non-blank string");
        }
        return value;
    }

    private static String string(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new MetadataException(path + " must be a string");
        }
        return string;
    }

    private static boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw new MetadataException("Expected a boolean");
        }
        return bool;
    }

    private static int integer(Object value, String path) {
        if (!(value instanceof Long number)) {
            throw new MetadataException(path + " must be an integer");
        }
        try {
            return Math.toIntExact(number);
        } catch (ArithmeticException e) {
            throw new MetadataException(path + " is outside the integer range", e);
        }
    }
}
