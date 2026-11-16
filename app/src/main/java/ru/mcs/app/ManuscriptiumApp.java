package ru.mcs.app;

import ru.mcs.core.AppRestarter;
import ru.mcs.core.CoreBootstrap;
import ru.mcs.core.platform.MacOsSupport;

public final class ManuscriptiumApp {

    private ManuscriptiumApp() {
    }

    public static void main(String[] args) {
        String[] applicationArgs = AppRestarter.awaitPreviousProcess(args);
        // Свойства macOS должны быть заданы до инициализации AWT.
        MacOsSupport.installEarly();
        CoreBootstrap.start(applicationArgs);
    }
}
