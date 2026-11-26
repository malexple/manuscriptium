package ru.mcs.spi.swing.editor.keymap;

import java.util.List;
import java.util.Map;
import ru.mcs.spi.swing.settings.Configurable;

/**
 * Сочетания клавиш редактора по областям. Плагин языка регистрирует области со значениями по умолчанию,
 * пользователь меняет сочетания в настройках, а редактор спрашивает итоговое сочетание через {@link #shortcut}.
 */
public interface KeymapService {

    /** Регистрирует область языка; область с тем же идентификатором заменяется. */
    void registerScope(String languageId, ShortcutScope scope);

    void unregisterScopes(String languageId);

    List<ShortcutScope> scopes(String languageId);

    /** Итоговое сочетание в записи {@link Shortcuts}; пустая строка — действие без сочетания. */
    String shortcut(String scopeId, String actionId);

    /** Переопределения пользователя: ключ {@link #key(String, String)}, значение — сочетание или пустая строка. */
    Map<String, String> overrides();

    void setOverrides(Map<String, String> overrides);

    /** Страница настроек со вкладками по областям языка. */
    Configurable createSettings(String languageId);

    void addChangeListener(Runnable listener);

    void removeChangeListener(Runnable listener);

    static String key(String scopeId, String actionId) {
        return scopeId + "|" + actionId;
    }
}
