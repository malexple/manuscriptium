package ru.mcs.spi.editor.style;

import java.awt.Font;
import java.util.List;
import ru.mcs.spi.editor.BlockKind;

/** Сервис CSS-плагина для редактора и других плагинов, не знающих реализацию движка. */
public interface EditorStyleService {

    List<EditorThemeInfo> availableThemes();

    String selectedTheme();

    void selectTheme(String id);

    void reload();

    StyleSheet styleSheet();

    List<CssDiagnostic> diagnostics();

    ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state);

    Font font(ComputedStyle style, float sizePx);
}
