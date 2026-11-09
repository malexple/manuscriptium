package ru.mcs.core.plugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Содержимое plugin.yml. {@code module} — имя корневого JPMS-модуля плагина;
 * {@code i18n} — база имени .properties-ресурсов в jar (например {@code ru/example/messages}) или null.
 */
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
        Contributions contributions,
        String i18n) {

    /** Плагин без вкладов и переводов, загружаемый при запуске. */
    public PluginDescriptor(String id, String name, String version, String sdkVersion, String module,
                            List<String> dependsOn, List<String> permissions, Path directory) {
        this(id, name, version, sdkVersion, module, dependsOn, permissions, directory,
                Activation.ON_STARTUP, Contributions.NONE, null);
    }

    /** Плагин без переводов. */
    public PluginDescriptor(String id, String name, String version, String sdkVersion, String module,
                            List<String> dependsOn, List<String> permissions, Path directory,
                            Activation activation, Contributions contributions) {
        this(id, name, version, sdkVersion, module, dependsOn, permissions, directory,
                activation, contributions, null);
    }
}
