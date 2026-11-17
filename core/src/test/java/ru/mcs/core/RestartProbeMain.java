package ru.mcs.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Отдельный JVM-процесс для интеграционной проверки restart; в продуктовый JAR не входит. */
public final class RestartProbeMain {

    private static final CountDownLatch RUNNING = new CountDownLatch(1);

    private RestartProbeMain() {
    }

    public static void main(String[] args) throws Exception {
        String[] applicationArgs = AppRestarter.awaitPreviousProcess(args);
        Path directory = Path.of(applicationArgs[0]);
        Files.createDirectories(directory);
        boolean initial = applicationArgs.length > 1 && applicationArgs[1].equals("initial");
        Files.writeString(directory.resolve(initial ? "initial.pid" : "restarted.pid"),
                Long.toString(ProcessHandle.current().pid()));
        System.out.println("restart-probe ready: " + (initial ? "initial" : "restarted"));
        if (initial) {
            System.setProperty("jdk.module.path", "");
            System.setProperty("jdk.module.main", "");
            System.setProperty("jdk.module.main.class", RestartProbeMain.class.getName());
            AppRestarter.startRestartProcess(new String[]{directory.toString(), "restarted",
                    "--home=" + directory});
            System.exit(0);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.writeString(directory.resolve("stopped"), "true");
            } catch (Exception ignored) {
                // Проверка завершится по исчезновению процесса, даже если marker не удалось записать.
            }
        }));
        RUNNING.await();
    }
}
