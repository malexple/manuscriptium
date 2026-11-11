package ru.mcs.app;

import ru.mcs.core.CoreBootstrap;
import ru.mcs.core.MacOsSupport;

public final class ManuscriptiumApp {

    private ManuscriptiumApp() {
    }

    public static void main(String[] args) {
        // Свойства macOS должны быть заданы до инициализации AWT.
        MacOsSupport.installEarly();
        CoreBootstrap.start(args);
    }
}
