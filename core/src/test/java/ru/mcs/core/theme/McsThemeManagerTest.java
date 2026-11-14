package ru.mcs.core.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.settings.AppSettings;

class McsThemeManagerTest {

    @TempDir
    Path directory;

    @Test
    void listsBuiltInAndCustomThemesAndSwitchesWithoutRestart() throws Exception {
        Files.writeString(directory.resolve("custom.json"), customTheme("#010203"));
        AppSettings settings = new AppSettings();
        McsThemeManager manager = new McsThemeManager(settings, directory, () -> Optional.of(false), 50);

        assertThat(manager.getThemes()).extracting(McsThemeManager.ThemeInfo::id)
                .containsExactly("light", "dark", "custom");
        SwingUtilities.invokeAndWait(() -> manager.setTheme("dark"));
        assertThat(settings.theme()).isEqualTo("dark");
        assertThat(manager.isDark()).isTrue();
        assertThat(FlatLaf.isLafDark()).isTrue();

        SwingUtilities.invokeAndWait(() -> manager.setTheme("custom"));
        assertThat(manager.isDark()).isFalse();
        assertThat((Color) UIManager.get("Label.foreground")).isEqualTo(new Color(1, 2, 3));
        assertThatThrownBy(() -> manager.setTheme("missing")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void watchServiceReloadsEditedJsonAndReplacesLiveTokens() throws Exception {
        Path file = directory.resolve("custom.json");
        Files.writeString(file, customTheme("#010203"));
        AppSettings settings = new AppSettings();
        try (McsThemeManager manager = new McsThemeManager(settings, directory, () -> Optional.of(false), 40)) {
            manager.start();
            SwingUtilities.invokeAndWait(() -> manager.setTheme("custom"));
            await(() -> new Color(1, 2, 3).equals(UIManager.get("Label.foreground")), "initial custom token");
            assertThat(directory.resolve("light.json")).exists();
            assertThat(directory.resolve("dark.json")).exists();

            Files.writeString(file, customTheme("#040506"));

            await(() -> new Color(4, 5, 6).equals(UIManager.get("Label.foreground")), "reloaded custom token");
            assertThat(settings.theme()).isEqualTo("custom");
        }
    }

    @Test
    void systemModeFollowsDetectorChanges() throws Exception {
        AtomicBoolean systemDark = new AtomicBoolean(false);
        AppSettings settings = new AppSettings();
        try (McsThemeManager manager = new McsThemeManager(
                settings, directory, () -> Optional.of(systemDark.get()), 40)) {
            manager.start();
            await(() -> !manager.isDark(), "initial light system theme");

            systemDark.set(true);

            await(manager::isDark, "dark system theme");
            await(FlatLaf::isLafDark, "dark FlatLaf base");
            assertThat(settings.theme()).isEqualTo(AppSettings.SYSTEM_THEME);
        }
    }

    @Test
    void shellThemeListenersReceiveEffectiveDarkMode() throws Exception {
        AppSettings settings = new AppSettings();
        McsThemeManager manager = new McsThemeManager(settings, directory, () -> Optional.of(false), 50);
        AtomicReference<ru.mcs.spi.editor.style.ShellThemeService.Snapshot> received = new AtomicReference<>();
        manager.addThemeListener(received::set);

        SwingUtilities.invokeAndWait(() -> manager.setTheme("dark"));

        assertThat(received.get()).isNotNull();
        assertThat(received.get().selectedId()).isEqualTo("dark");
        assertThat(received.get().dark()).isTrue();
    }

    private static String customTheme(String foreground) {
        return """
                {
                  "id": "custom",
                  "name": "Custom",
                  "dark": false,
                  "tokens": {"Label.foreground": "%s"}
                }
                """.formatted(foreground);
    }

    private static void await(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }
}
