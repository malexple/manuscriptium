package ru.mcs.core.plugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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
        return new PluginDescriptor(
                id,
                optional(map, "name", id),
                required(map, "version"),
                required(map, "sdk-version"),
                required(map, "module"),
                list(map, "depends-on"),
                list(map, "permissions"),
                directory);
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
