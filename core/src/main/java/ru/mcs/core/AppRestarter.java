package ru.mcs.core;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongFunction;
import javax.swing.JOptionPane;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.shell.ShellFrame;

/** Перезапуск той же JVM-конфигурации с исходными аргументами приложения. */
public final class AppRestarter {

    private static final String MAIN_CLASS = "ru.mcs.app.ManuscriptiumApp";
    private static final String RESTART_PARENT_PREFIX = "--mcs-restart-parent=";
    private static final long PREVIOUS_PROCESS_TIMEOUT_MS = 30_000;

    private AppRestarter() {
    }

    /** Ждёт выбранные пользователем сохранения и только затем запускает новый процесс. */
    public static void restart(ShellFrame frame, String[] args) {
        frame.editors().confirmAllAsync().thenAccept(confirmed -> {
            if (confirmed) {
                launch(frame, args);
            }
        });
    }

    private static void launch(ShellFrame frame, String[] args) {
        try {
            startRestartProcess(args);
            System.exit(0);
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(frame, Messages.get("plugins.restart.error", e.toString()),
                    Messages.get("plugins.restart.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    static Process startRestartProcess(String[] args) throws IOException {
        long parentPid = ProcessHandle.current().pid();
        List<String> command = restartCommand(System.getProperties(),
                ManagementFactory.getRuntimeMXBean().getInputArguments(), args,
                System.getProperty("os.name", ""), parentPid);
        Path log = restartLog(args);
        try {
            Files.createDirectories(log.getParent());
            appendLog(log, "=== Restart requested at " + Instant.now() + ", parent pid=" + parentPid + " ===");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                    .start();
            appendLog(log, "Restart process started, pid=" + process.pid());
            return process;
        } catch (IOException | RuntimeException error) {
            try {
                appendLog(log, "Restart process failed: " + error);
            } catch (IOException loggingError) {
                error.addSuppressed(loggingError);
            }
            throw error;
        }
    }

    /** Журнал не зависит от консоли Gradle/IDE и сохраняет ошибки раннего запуска нового процесса. */
    static Path restartLog(String[] args) {
        String home = null;
        for (String arg : args) {
            if (arg.startsWith("--home=")) {
                home = arg.substring("--home=".length());
            }
        }
        return HomeDirs.resolve(home).resolve("logs").resolve("restart.log").toAbsolutePath().normalize();
    }

    private static void appendLog(Path log, String message) throws IOException {
        Files.writeString(log, message + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static List<String> restartCommand(Properties properties, List<String> jvmArgs, String[] args,
                                       String osName, long parentPid) {
        if (parentPid <= 0) {
            throw new IllegalArgumentException("Parent process id must be positive");
        }
        List<String> command = command(properties, jvmArgs, args, osName);
        command.add(RESTART_PARENT_PREFIX + parentPid);
        return command;
    }

    /**
     * Не даёт новому процессу открыть SQLite/Lucene до завершения shutdown hook прежнего процесса.
     * Возвращает аргументы приложения без служебного restart-аргумента.
     */
    public static String[] awaitPreviousProcess(String[] args) {
        return awaitPreviousProcess(args, AppRestarter::processExit, PREVIOUS_PROCESS_TIMEOUT_MS);
    }

    static String[] awaitPreviousProcess(String[] args, LongFunction<CompletableFuture<?>> processExits,
                                         long timeoutMillis) {
        if (args.length == 0 || !args[args.length - 1].startsWith(RESTART_PARENT_PREFIX)) {
            return args;
        }
        String parentArgument = args[args.length - 1];
        long parentPid;
        try {
            parentPid = Long.parseLong(parentArgument.substring(RESTART_PARENT_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid restart parent process id", e);
        }
        String[] applicationArgs = Arrays.copyOf(args, args.length - 1);
        try {
            processExits.apply(parentPid).get(timeoutMillis, TimeUnit.MILLISECONDS);
            return applicationArgs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the previous application process", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Previous application process did not stop within "
                    + timeoutMillis + " ms", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot wait for the previous application process", e.getCause());
        }
    }

    private static CompletableFuture<?> processExit(long pid) {
        return ProcessHandle.of(pid)
                .<CompletableFuture<?>>map(ProcessHandle::onExit)
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    static List<String> command(Properties properties, List<String> jvmArgs, String[] args, String osName) {
        String executable = osName.toLowerCase(Locale.ROOT).contains("windows") ? "java.exe" : "java";
        Path java = Path.of(properties.getProperty("java.home"), "bin", executable);
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        addJvmArguments(command, jvmArgs);

        String modulePath = properties.getProperty("jdk.module.path", "");
        String mainModule = properties.getProperty("jdk.module.main", "");
        if (!modulePath.isBlank() && !mainModule.isBlank()) {
            command.add("-p");
            command.add(modulePath);
            command.add("-m");
            String mainClass = properties.getProperty("jdk.module.main.class", "");
            command.add(mainClass.isBlank() ? mainModule : mainModule + "/" + mainClass);
        } else {
            String classPath = properties.getProperty("java.class.path", "");
            if (classPath.isBlank()) {
                throw new IllegalStateException("Cannot determine application module path or classpath");
            }
            command.add("-cp");
            command.add(classPath);
            command.add(properties.getProperty("jdk.module.main.class", MAIN_CLASS));
        }
        command.addAll(List.of(args));
        return command;
    }

    private static void addJvmArguments(List<String> command, List<String> jvmArgs) {
        for (int i = 0; i < jvmArgs.size(); i++) {
            String argument = jvmArgs.get(i);
            if (argument.equals("-p") || argument.equals("--module-path")
                    || argument.equals("-m") || argument.equals("--module")) {
                i++;
                continue;
            }
            if (argument.startsWith("--module-path=") || argument.startsWith("--module=")
                    || argument.startsWith("-Djdk.module.path=")
                    || argument.startsWith("-Djdk.module.main=")
                    || argument.startsWith("-Djdk.module.main.class=")) {
                continue;
            }
            command.add(argument);
        }
    }
}
