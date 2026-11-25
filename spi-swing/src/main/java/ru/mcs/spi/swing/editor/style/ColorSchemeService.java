package ru.mcs.spi.swing.editor.style;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Цвета токенов подсветки. Плагины регистрируют группы токенов со значениями по умолчанию, пользователь меняет цвета
 * на странице «Цветовая схема», а подсветка спрашивает итоговый цвет через {@link #color(String, boolean)}.
 */
public interface ColorSchemeService {

    /** Регистрирует группу токенов; группа с тем же идентификатором заменяется. */
    void registerGroup(TokenGroup group);

    void unregisterGroup(String id);

    List<TokenGroup> groups();

    /** Цвет токена на светлом или тёмном фоне: переопределение пользователя или значение по умолчанию; иначе null. */
    Color color(String token, boolean dark);

    /** Переопределения пользователя: ключ {@link #key(String, boolean)}, значение RGB. */
    Map<String, Integer> overrides();

    /** Заменяет все переопределения; неверные ключи и значения отбрасываются. */
    void setOverrides(Map<String, Integer> overrides);

    void addChangeListener(Runnable listener);

    void removeChangeListener(Runnable listener);

    static String key(String token, boolean dark) {
        return token + (dark ? ":dark" : ":light");
    }
}
