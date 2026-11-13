package ru.mcs.core;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.swing.JOptionPane;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.shell.ShellFrame;

/** Перезапуск той же JVM-конфигурации с исходными аргументами приложения. */
public final class AppRestarter {

    private static final String MAIN_CLASS = "ru.mcs.app.ManuscriptiumApp";

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
            List<String> command = command(System.getProperties(),
                    ManagementFactory.getRuntimeMXBean().getInputArguments(), args,
                    System.getProperty("os.name", ""));
            new ProcessBuilder(command).inheritIO().start();
            System.exit(0);
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(frame, Messages.get("plugins.restart.error", e.toString()),
                    Messages.get("plugins.restart.title"), JOptionPane.ERROR_MESSAGE);
        }
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
