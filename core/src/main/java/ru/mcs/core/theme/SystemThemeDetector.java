package ru.mcs.core.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Лёгкий детектор ОС без JNA/native-зависимостей. empty означает, что механизм недоступен. */
@FunctionalInterface
interface SystemThemeDetector {

    Optional<Boolean> detect();

    static SystemThemeDetector platform() {
        return new CommandSystemThemeDetector();
    }
}

final class CommandSystemThemeDetector implements SystemThemeDetector {

    private static final Pattern HEX = Pattern.compile("0x([0-9a-fA-F]+)");
    private static final long COMMAND_TIMEOUT_MS = 1500;

    private final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    @Override
    public Optional<Boolean> detect() {
        try {
            if (osName.contains("windows")) {
                return command("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme").flatMap(CommandSystemThemeDetector::parseWindows);
            }
            if (osName.contains("mac")) {
                // У светлой темы macOS ключ AppleInterfaceStyle отсутствует, и defaults завершается с ошибкой.
                return Optional.of(parseMac(command("defaults", "read", "-g", "AppleInterfaceStyle")));
            }
            if (osName.contains("linux")) {
                Optional<String> colorScheme = command("gsettings", "get",
                        "org.gnome.desktop.interface", "color-scheme");
                if (colorScheme.isPresent()) {
                    return colorScheme.flatMap(CommandSystemThemeDetector::parseLinux);
                }
                return command("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
                        .flatMap(CommandSystemThemeDetector::parseLinux);
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    static Optional<Boolean> parseWindows(String output) {
        Matcher matcher = HEX.matcher(output);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1), 16) == 0);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static boolean parseMac(Optional<String> output) {
        return output.map(value -> value.toLowerCase(Locale.ROOT).contains("dark")).orElse(false);
    }

    static Optional<Boolean> parseLinux(String output) {
        String value = output.toLowerCase(Locale.ROOT);
        if (value.contains("dark")) {
            return Optional.of(true);
        }
        if (value.contains("light") || value.contains("default")) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private static Optional<String> command(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            return Optional.empty();
        }
        if (process.exitValue() != 0) {
            return Optional.empty();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return Optional.of(output);
    }
}
