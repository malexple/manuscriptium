package ru.mcs.core;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.desktop.QuitHandler;
import java.util.Locale;
import java.util.Properties;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/** Точечная интеграция с macOS. На остальных платформах все методы сохраняют обычное поведение. */
public final class MacOsSupport {

    static final int WINDOW_BUTTONS_INSET = 70;

    private MacOsSupport() {
    }

    public static boolean isMacOs() {
        return isMacOs(System.getProperty("os.name", ""));
    }

    static boolean isMacOs(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    /** Свойства AWT должны быть установлены до первого обращения к AWT/Swing. */
    public static void installEarly() {
        installEarly(System.getProperties(), isMacOs());
    }

    static void installEarly(Properties properties, boolean macOs) {
        if (!macOs) {
            return;
        }
        properties.setProperty("apple.laf.useScreenMenuBar", "true");
        properties.setProperty("apple.awt.application.name", "Manuscriptium");
        properties.setProperty("apple.awt.application.appearance", "system");
    }

    /**
     * Расширяет содержимое окна в прозрачный заголовок и резервирует место под кнопки окна.
     * Отступ находится снаружи toolbar, поэтому не исчезает при его {@code removeAll()/refresh()}.
     */
    public static JComponent unifiedToolbar(JFrame frame, JComponent toolbar) {
        return unifiedToolbar(frame.getRootPane(), toolbar, isMacOs());
    }

    static JComponent unifiedToolbar(JRootPane rootPane, JComponent toolbar, boolean macOs) {
        if (!macOs) {
            return toolbar;
        }
        rootPane.putClientProperty("apple.awt.fullWindowContent", true);
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true);
        rootPane.putClientProperty("apple.awt.windowTitleVisible", false);

        JPanel container = new JPanel(new BorderLayout());
        container.add(Box.createHorizontalStrut(WINDOW_BUTTONS_INSET), BorderLayout.WEST);
        container.add(toolbar, BorderLayout.CENTER);
        return container;
    }

    /** Подключает About, Preferences и Quit из нативного меню приложения к общим операциям ядра. */
    public static void installApplicationMenu(Runnable about, Runnable preferences, Runnable quit) {
        if (!isMacOs() || !Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> onEventThread(about));
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(event -> onEventThread(preferences));
        }
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler(quitHandler(quit));
        }
    }

    static QuitHandler quitHandler(Runnable quit) {
        return (event, response) -> {
            // Выход выполнит ShellFrame только после подтверждения всех несохранённых редакторов.
            response.cancelQuit();
            onEventThread(quit);
        };
    }

    private static void onEventThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
