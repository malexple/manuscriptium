package ru.mcs.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AppRestarterTest {

    @Test
    void rebuildsModuleLaunchWithJvmAndApplicationArguments() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("jdk.module.path", "/runtime/app");
        properties.setProperty("jdk.module.main", "ru.mcs.app");
        properties.setProperty("jdk.module.main.class", "ru.mcs.app.ManuscriptiumApp");

        List<String> command = AppRestarter.command(properties, List.of("-Dfile.encoding=UTF-8"),
                new String[] {"--home=/data"}, "Linux");

        assertThat(command).containsExactly(Path.of("/runtime", "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-p", "/runtime/app",
                "-m", "ru.mcs.app/ru.mcs.app.ManuscriptiumApp", "--home=/data");
    }

    @Test
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

        assertThat(command).containsExactly(Path.of("/runtime", "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-Xmx64m", "-p", "/runtime/app",
                "-m", "ru.mcs.app/ru.mcs.app.ManuscriptiumApp");
    }

    @Test
    void fallsBackToClasspathAndWindowsExecutable() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("java.class.path", "/app/classes");

        List<String> command = AppRestarter.command(properties, List.of(), new String[0], "Windows 11");

        assertThat(command).containsExactly(Path.of("/runtime", "bin", "java.exe").toString(),
                "-cp", "/app/classes", "ru.mcs.app.ManuscriptiumApp");
    }

    @Test
    void restartCommandAddsParentProcessHandshake() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("jdk.module.path", "/runtime/app");
        properties.setProperty("jdk.module.main", "ru.mcs.app");
        properties.setProperty("jdk.module.main.class", "ru.mcs.app.ManuscriptiumApp");

        List<String> command = AppRestarter.restartCommand(properties, List.of(),
                new String[] {"--home=/data"}, "Linux", 4242);

        assertThat(command).endsWith("--home=/data", "--mcs-restart-parent=4242");
    }

    @Test
    void waitsForPreviousProcessAndRemovesInternalArgument() {
        AtomicBoolean exited = new AtomicBoolean();
        CompletableFuture<?> exit = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            exited.set(true);
        });

        String[] args = AppRestarter.awaitPreviousProcess(
                new String[] {"--home=/data", "--mcs-restart-parent=4242"}, ignored -> exit, 1000);

        assertThat(exited).isTrue();
        assertThat(args).containsExactly("--home=/data");
    }

    @Test
    void missingPreviousProcessStillRemovesInternalArgument() {
        String[] args = AppRestarter.awaitPreviousProcess(
                new String[] {"--home=/data", "--mcs-restart-parent=4242"},
                ignored -> CompletableFuture.completedFuture(null), 1000);

        assertThat(args).containsExactly("--home=/data");
    }

    @Test
    void ordinaryLaunchDoesNotWaitOrChangeArguments() {
        String[] original = {"--home=/data"};

        String[] args = AppRestarter.awaitPreviousProcess(original,
                ignored -> new CompletableFuture<>(), 1);

        assertThat(args).isSameAs(original);
    }

    @Test
    void previousProcessTimeoutFailsBeforeOpeningProject() {
        assertThatThrownBy(() -> AppRestarter.awaitPreviousProcess(
                new String[] {"--mcs-restart-parent=4242"}, ignored -> new CompletableFuture<>(), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void missingLaunchConfigurationFailsBeforeClosingApplication() {
        Properties properties = new Properties();
        properties.setProperty("java.home", "/runtime");
        properties.setProperty("java.class.path", "");

        assertThatThrownBy(() -> AppRestarter.command(properties, List.of(), new String[0], "Linux"))
                .isInstanceOf(IllegalStateException.class);
    }
}
