package ru.mcs.core.theme;

import java.nio.file.Path;
import java.util.Map;

/** Неизменяемое описание темы. source == null у встроенного резервного варианта. */
record ThemeDefinition(String id, String name, boolean dark, Map<String, Object> tokens, Path source) {

    ThemeDefinition {
        tokens = Map.copyOf(tokens);
    }
}
