package ru.mcs.markdown.editor;

import ru.mcs.spi.i18n.I18n;

/** Локализация перенесённых редакторов; до старта модуля безопасно возвращает ключи. */
public final class MarkdownMessages {

    private static volatile I18n messages = I18n.passthrough();

    private MarkdownMessages() {
    }

    public static void install(I18n i18n) {
        messages = java.util.Objects.requireNonNull(i18n);
    }

    public static String get(String key, Object... args) {
        return messages.get(key, args);
    }
}
