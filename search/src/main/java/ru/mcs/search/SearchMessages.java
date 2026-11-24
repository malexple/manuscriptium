package ru.mcs.search;

import ru.mcs.spi.i18n.I18n;

/** Строки модуля поиска; язык задаёт приложение через I18n контекста модуля. */
final class SearchMessages {

    private static volatile I18n messages = I18n.passthrough();

    private SearchMessages() {
    }

    static void install(I18n i18n) {
        messages = java.util.Objects.requireNonNull(i18n);
    }

    static String get(String key, Object... args) {
        return messages.get(key, args);
    }
}
