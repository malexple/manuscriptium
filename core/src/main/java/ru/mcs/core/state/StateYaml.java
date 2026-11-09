package ru.mcs.core.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** YAML-представление состояния: допускает только Map(String→…), List, String, Number, Boolean, null. Ключи сортируются. */
public final class StateYaml {

    private StateYaml() {
    }

    public static Object normalize(Object value, String path) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(path + ": map keys must be strings");
                }
                out.put(key, normalize(e.getValue(), path + "." + key));
            }
            return out;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> out = new ArrayList<>();
            int i = 0;
            for (Object item : collection) {
                out.add(normalize(item, path + "[" + (i++) + "]"));
            }
            return out;
        }
        throw new IllegalArgumentException(path + ": unsupported type " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static String dump(Map<String, Object> state) {
        Map<String, Object> normalized = (Map<String, Object>) normalize(state, "$");
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setSplitLines(false);
        return new Yaml(options).dump(normalized);
    }

    /** @return состояние или пустая Map, если корень документа не mapping */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String text) {
        Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (root instanceof Map<?, ?>) {
            return (Map<String, Object>) normalize(root, "$");
        }
        return Map.of();
    }
}
