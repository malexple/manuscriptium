package ru.mcs.core.theme;

import java.io.Reader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Строго проверенная JSON-модель темы. SnakeYAML читает JSON без дополнительной зависимости. */
final class ThemeJson {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]+");

    private ThemeJson() {
    }

    static ThemeDefinition parse(Reader reader, String fallbackId, Path source) {
        Object raw = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Theme JSON must be an object");
        }
        String id = string(map, "id", fallbackId);
        if (id == null || !ID.matcher(id).matches() || McsThemeManager.SYSTEM.equals(id)) {
            throw new IllegalArgumentException("Invalid or reserved theme id: " + id);
        }
        String name = string(map, "name", id);
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (!(map.get("dark") instanceof Boolean dark)) {
            throw new IllegalArgumentException("Theme '" + id + "' requires boolean 'dark'");
        }
        Map<String, Object> tokens = new LinkedHashMap<>();
        Object rawTokens = map.get("tokens");
        if (rawTokens != null && !(rawTokens instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Theme '" + id + "': 'tokens' must be an object");
        }
        if (rawTokens instanceof Map<?, ?> tokenMap) {
            for (Map.Entry<?, ?> entry : tokenMap.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw new IllegalArgumentException("Theme '" + id + "': token key must be non-blank string");
                }
                tokens.put(key, ThemeTokens.toUiValue(entry.getValue()));
            }
        }
        return new ThemeDefinition(id, name, dark, tokens, source);
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }
}
