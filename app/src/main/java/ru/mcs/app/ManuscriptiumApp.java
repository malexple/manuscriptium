package ru.mcs.app;

import java.util.Locale;
import ru.mcs.core.CoreBootstrap;

public final class ManuscriptiumApp {

    private ManuscriptiumApp() {
    }

    public static void main(String[] args) {
        // Свойства macOS должны быть заданы до инициализации AWT.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "Manuscriptium");
            System.setProperty("apple.awt.application.appearance", "system");
        }
        CoreBootstrap.start(args);
    }
}
