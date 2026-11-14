package ru.mcs.spi.editor.style;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Read-only мост к JSON/FlatLaf темам оболочки; CSS-плагин не зависит от core. */
public interface ShellThemeService {

    record Snapshot(String selectedId, boolean dark) {
    }

    String selectedTheme();

    boolean isDark();

    Path directory();

    void addThemeListener(Consumer<Snapshot> listener);

    void removeThemeListener(Consumer<Snapshot> listener);
}
