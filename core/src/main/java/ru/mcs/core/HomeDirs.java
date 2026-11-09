package ru.mcs.core;

import java.nio.file.Path;
import java.util.Locale;

/** Каталоги данных приложения (глобальные настройки и плагины). */
final class HomeDirs {

    private HomeDirs() {
    }

    static Path resolve(String override) {
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath();
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
}
