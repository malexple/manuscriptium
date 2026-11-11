package ru.mcs.core.plugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import ru.mcs.spi.ui.ToolWindowAnchor;

public final class PluginDescriptorParser {

    private PluginDescriptorParser() {
    }

    public static PluginDescriptor parse(Path ymlFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(ymlFile, StandardCharsets.UTF_8)) {
            return parse(reader, ymlFile.toAbsolutePath().getParent());
        }
    }

    public static PluginDescriptor parse(Reader reader, Path directory) {
        Object raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("plugin.yml must be a YAML mapping");
        }
        String id = required(map, "id");
        String i18n = optional(map, "i18n", "");
        return new PluginDescriptor(
                id,
                optional(map, "name", id),
                required(map, "version"),
                required(map, "sdk-version"),
                required(map, "module"),
                list(map, "depends-on"),
                list(map, "permissions"),
                directory,
                activation(map),
                contributions(map),
                i18n.isBlank() ? null : i18n);
    }

    private static Activation activation(Map<?, ?> map) {
        String value = optional(map, "activation", "onStartup");
        return switch (value) {
            case "onStartup" -> Activation.ON_STARTUP;
            case "onDemand" -> Activation.ON_DEMAND;
            default -> throw new IllegalArgumentException(
                    "plugin.yml: unknown activation '" + value + "' (expected onStartup or onDemand)");
        };
    }

    private static Contributions contributions(Map<?, ?> root) {
        Object raw = root.get("contributions");
        if (raw == null) {
            return Contributions.NONE;
        }
        if (!(raw instanceof Map<?, ?> c)) {
            throw new IllegalArgumentException("plugin.yml: 'contributions' must be a mapping");
        }

        Set<String> seen = new HashSet<>();
        List<Contributions.ActionGroupDecl> groups = new ArrayList<>();
        for (Map<?, ?> m : maps(c, "actionGroups")) {
            groups.add(new Contributions.ActionGroupDecl(uniqueId(m, seen, "actionGroups"),
                    required(m, "text"), bool(m, "popup", true), list(m, "groups"), optional(m, "icon", null)));
        }

        seen = new HashSet<>();
        List<Contributions.ActionDecl> actions = new ArrayList<>();
        for (Map<?, ?> m : maps(c, "actions")) {
            actions.add(new Contributions.ActionDecl(uniqueId(m, seen, "actions"),
                    required(m, "text"), list(m, "groups"), optional(m, "icon", null)));
        }

        seen = new HashSet<>();
        List<Contributions.ToolWindowDecl> toolWindows = new ArrayList<>();
        for (Map<?, ?> m : maps(c, "toolWindows")) {
            String id = uniqueId(m, seen, "toolWindows");
            String anchorText = required(m, "anchor");
            ToolWindowAnchor anchor;
            try {
                anchor = ToolWindowAnchor.valueOf(anchorText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("plugin.yml: toolWindows '" + id
                        + "' has unknown anchor '" + anchorText + "' (left, right or bottom)");
            }
            toolWindows.add(new Contributions.ToolWindowDecl(id, required(m, "title"), anchor,
                    bool(m, "show-by-default", false), optional(m, "icon", null)));
        }

        seen = new HashSet<>();
        List<Contributions.EditorDecl> editors = new ArrayList<>();
        for (Map<?, ?> m : maps(c, "fileEditors")) {
            String id = uniqueId(m, seen, "fileEditors");
            List<String> patterns = list(m, "patterns");
            if (patterns.isEmpty()) {
                throw new IllegalArgumentException("plugin.yml: fileEditors '" + id + "' needs non-empty 'patterns'");
            }
            editors.add(new Contributions.EditorDecl(id, patterns, integer(m, "priority", 0)));
        }
        seen = new HashSet<>();
        List<Contributions.SettingsDecl> settings = new ArrayList<>();
        for (Map<?, ?> m : maps(c, "settings")) {
            String parentId = optional(m, "parent-id", "");
            settings.add(new Contributions.SettingsDecl(uniqueId(m, seen, "settings"),
                    required(m, "title"), parentId.isBlank() ? null : parentId));
        }
        return new Contributions(groups, actions, toolWindows, editors, settings);
    }

    private static String uniqueId(Map<?, ?> map, Set<String> seen, String kind) {
        String id = required(map, "id");
        if (!seen.add(id)) {
            throw new IllegalArgumentException("plugin.yml: Duplicate id '" + id + "' in " + kind);
        }
        return id;
    }

    private static List<Map<?, ?>> maps(Map<?, ?> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof Collection<?> items)) {
            throw new IllegalArgumentException("plugin.yml: '" + key + "' must be a list");
        }
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("plugin.yml: entries of '" + key + "' must be mappings");
            }
            result.add(m);
        }
        return result;
    }

    private static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException("plugin.yml: field '" + key + "' must be true or false");
    }

    private static int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException("plugin.yml: field '" + key + "' must be a number");
    }

    private static String required(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("plugin.yml: missing required field '" + key + "'");
        }
        return String.valueOf(value).trim();
    }

    private static String optional(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private static List<String> list(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).toList();
        }
        throw new IllegalArgumentException("plugin.yml: field '" + key + "' must be a list");
    }
}
