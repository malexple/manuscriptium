package ru.mcs.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HomeDirsTest {

    private final String originalHome = System.getProperty("manuscriptium.home");
    private final String originalSystemPlugins = System.getProperty("manuscriptium.systemPlugins");

    @AfterEach
    void restoreProperties() {
        restore("manuscriptium.home", originalHome);
        restore("manuscriptium.systemPlugins", originalSystemPlugins);
    }

    @Test
    void commandLineHomeHasPriorityOverDevelopmentProperty() {
        System.setProperty("manuscriptium.home", "build/property-home");
        Path commandLine = Path.of("build", "argument-home").toAbsolutePath();

        assertThat(HomeDirs.resolve("build/argument-home")).isEqualTo(commandLine);
    }

    @Test
    void developmentHomePropertyIsUsedWithoutCommandLineOverride() {
        Path configured = Path.of("build", "property-home").toAbsolutePath();
        System.setProperty("manuscriptium.home", configured.toString());

        assertThat(HomeDirs.resolve(null)).isEqualTo(configured);
    }

    @Test
    void systemPluginsDirectoryCanBeOverriddenForDevelopmentAndCustomLaunchers() {
        Path configured = Path.of("build", "custom-system-plugins").toAbsolutePath();
        System.setProperty("manuscriptium.systemPlugins", configured.toString());

        assertThat(HomeDirs.systemPluginsDir()).isEqualTo(configured);
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
