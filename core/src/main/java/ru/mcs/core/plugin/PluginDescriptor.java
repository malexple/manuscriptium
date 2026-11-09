package ru.mcs.core.plugin;

import java.nio.file.Path;
import java.util.List;

/** Содержимое plugin.yml. {@code module} — имя корневого JPMS-модуля плагина. */
public record PluginDescriptor(
        String id,
        String name,
        String version,
        String sdkVersion,
        String module,
        List<String> dependsOn,
        List<String> permissions,
        Path directory) {
}
