package ru.mcs.core.theme;

import java.nio.file.Path;
import java.util.List;

/** Узкий контракт выбора темы для страниц настроек и других потребителей платформы. */
public interface ThemeController {

    String SYSTEM = "system";

    record ThemeInfo(String id, String name, boolean dark) {
    }

    Path directory();

    String selectedTheme();

    List<ThemeInfo> getThemes();

    void setTheme(String id);
}
