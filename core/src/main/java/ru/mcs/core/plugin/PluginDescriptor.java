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
        Path directory,
        Activation activation,
        Contributions contributions) {

    /** Плагин без вкладов, загружаемый при запуске. */
    public PluginDescriptor(String id, String name, String version, String sdkVersion, String module,
                            List<String> dependsOn, List<String> permissions, Path directory) {
        this(id, name, version, sdkVersion, module, dependsOn, permissions, directory,
                Activation.ON_STARTUP, Contributions.NONE);
    }
}
