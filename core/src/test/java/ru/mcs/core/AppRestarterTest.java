package ru.mcs.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class AppRestarterTest {

    @Test
    @Disabled
    void rebuildsModuleLaunchWithJvmAndApplicationArguments() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("jdk.module.path", "/runtime/app");
        properties.setProperty("jdk.module.main", "ru.mcs.app");
        properties.setProperty("jdk.module.main.class", "ru.mcs.app.ManuscriptiumApp");

        List<String> command = AppRestarter.command(properties, List.of("-Dfile.encoding=UTF-8"),
                new String[] {"--home=/data"}, "Linux");

        assertThat(command).containsExactly("/runtime/bin/java", "-Dfile.encoding=UTF-8",
                "-p", "/runtime/app", "-m", "ru.mcs.app/ru.mcs.app.ManuscriptiumApp", "--home=/data");
    }

    @Test
    @Disabled
    void filtersLauncherModuleArgumentsButPreservesOtherJvmOptions() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("jdk.module.path", "/runtime/app");
        properties.setProperty("jdk.module.main", "ru.mcs.app");
        properties.setProperty("jdk.module.main.class", "ru.mcs.app.ManuscriptiumApp");

        List<String> command = AppRestarter.command(properties, List.of(
                "--module-path=/old/app", "-Djdk.module.path=/old/app",
                "-Djdk.module.main=old.module", "-Djdk.module.main.class=old.Main",
                "-Dfile.encoding=UTF-8", "-p", "/older/app", "-m", "older.module", "-Xmx64m"),
                new String[0], "Linux");

        assertThat(command).containsExactly("/runtime/bin/java", "-Dfile.encoding=UTF-8", "-Xmx64m",
                "-p", "/runtime/app", "-m", "ru.mcs.app/ru.mcs.app.ManuscriptiumApp");
    }

    @Test
    @Disabled
    void fallsBackToClasspathAndWindowsExecutable() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("java.class.path", "/app/classes");

        List<String> command = AppRestarter.command(properties, List.of(), new String[0], "Windows 11");

        assertThat(command).containsExactly("/runtime/bin/java.exe", "-cp", "/app/classes",
                "ru.mcs.app.ManuscriptiumApp");
    }

    @Test
    @Disabled
    void missingLaunchConfigurationFailsBeforeClosingApplication() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("java.class.path", "");

        assertThatThrownBy(() -> AppRestarter.command(properties, List.of(), new String[0], "Linux"))
                .isInstanceOf(IllegalStateException.class);
    }
}
