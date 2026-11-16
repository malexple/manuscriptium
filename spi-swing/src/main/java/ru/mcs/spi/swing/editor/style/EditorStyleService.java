package ru.mcs.spi.swing.editor.style;

import java.awt.Font;
import java.util.List;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.EditorThemeInfo;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleSheet;

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

    /** Вызывается после атомарной публикации нового StyleSheet; поток определяется реализацией. */
    default void addChangeListener(Runnable listener) {
    }

    default void removeChangeListener(Runnable listener) {
    }
}
