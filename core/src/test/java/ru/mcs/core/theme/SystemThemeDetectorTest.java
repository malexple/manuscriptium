package ru.mcs.core.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SystemThemeDetectorTest {

    @Test
    void parsesWindowsPersonalizeRegistryValue() {
        assertThat(CommandSystemThemeDetector.parseWindows(
                "AppsUseLightTheme    REG_DWORD    0x0")).contains(true);
        assertThat(CommandSystemThemeDetector.parseWindows(
                "AppsUseLightTheme    REG_DWORD    0x1")).contains(false);
        assertThat(CommandSystemThemeDetector.parseWindows("not found")).isEmpty();
    }

    @Test
    void missingMacInterfaceStyleMeansLightAndAllowsSwitchingBackFromDark() {
        assertThat(CommandSystemThemeDetector.parseMac(Optional.of("Dark"))).isTrue();
        assertThat(CommandSystemThemeDetector.parseMac(Optional.empty())).isFalse();
    }

    @Test
    void parsesFreedesktopAndGtkValues() {
        assertThat(CommandSystemThemeDetector.parseLinux("'prefer-dark'")).contains(true);
        assertThat(CommandSystemThemeDetector.parseLinux("'prefer-light'")).contains(false);
        assertThat(CommandSystemThemeDetector.parseLinux("'default'")).contains(false);
        assertThat(CommandSystemThemeDetector.parseLinux("'Adwaita-dark'")).contains(true);
        assertThat(CommandSystemThemeDetector.parseLinux("unknown")).isEmpty();
    }
}
