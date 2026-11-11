package ru.mcs.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Каталоги данных приложения (глобальные настройки и плагины). */
final class HomeDirs {

    private HomeDirs() {
    }

    static Path resolve(String override) {
        String configured = override != null ? override : System.getProperty("manuscriptium.home");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null ? Path.of(appData) : Path.of(userHome, "AppData", "Roaming");
            return base.resolve("Manuscriptium");
        }
        if (os.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "Manuscriptium");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(userHome, ".config");
        return base.resolve("manuscriptium");
    }

    static Path pluginsDir(Path home) {
        return home.resolve("plugins");
    }

    /** Bundled-плагины из installation/system-plugins; в Gradle run путь задаётся явно через system property. */
    static Path systemPluginsDir() {
        String override = System.getProperty("manuscriptium.systemPlugins");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath();
        }
        Path runtimePlugins = Path.of(System.getProperty("java.home"), "system-plugins");
        if (Files.isDirectory(runtimePlugins)) {
            return runtimePlugins;
        }
        // Нестандартный launcher может задать manuscriptium.systemPlugins явно.
        return Path.of("system-plugins").toAbsolutePath();
    }
}
