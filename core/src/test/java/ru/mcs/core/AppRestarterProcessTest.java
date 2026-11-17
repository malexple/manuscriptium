package ru.mcs.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppRestarterProcessTest {

    @TempDir
    Path tmp;

    @Test
    void realChildWaitsForPreviousProcessAndRemainsAlive() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Path log = tmp.resolve("process.log");
        Process initial = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                        RestartProbeMain.class.getName(), tmp.toString(), "initial")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        Path restartedPid = tmp.resolve("restarted.pid");
        awaitFile(restartedPid, Duration.ofSeconds(20));
        assertThat(initial.waitFor(5, TimeUnit.SECONDS))
                .as("new process must become ready only after the previous process exits; log: " + read(log))
                .isTrue();
        long childPid = Long.parseLong(Files.readString(restartedPid).trim());
        ProcessHandle child = ProcessHandle.of(childPid).orElseThrow();
        assertThat(child.isAlive()).as(read(log)).isTrue();
        Path restartLog = tmp.resolve("logs/restart.log");
        awaitFile(restartLog, Duration.ofSeconds(5));
        assertThat(read(restartLog))
                .contains("Restart requested", "Restart process started", "restart-probe ready: restarted")
                .doesNotContain("Exception", "Error");
        assertThat(read(log)).doesNotContain("restart-probe ready: restarted");

        child.destroy();
        child.onExit().get(5, TimeUnit.SECONDS);
        assertThat(read(restartLog)).doesNotContain("Exception", "Error");
    }

    private static void awaitFile(Path file, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!Files.isRegularFile(file) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(file).exists();
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file) : "<no process log>";
        } catch (Exception e) {
            return e.toString();
        }
    }
}
