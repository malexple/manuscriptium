package ru.mcs.spi.i18n;

import java.util.Locale;

/**
 * Локализация плагина. Строки лежат в .properties-файлах (UTF-8) внутри jar плагина; база имени
 * объявляется в plugin.yml полем {@code i18n: ru/example/plugin/messages}, файлы — {@code messages.properties}
 * (по умолчанию, английский) и {@code messages_ru.properties}. Хост читает их напрямую из jar, не загружая код плагина,
 * поэтому тексты вкладов из plugin.yml ({@code text: "%ключ"}) переводятся до активации плагина.
 * Язык выбирается при запуске приложения; смена языка применяется после перезапуска.
 */
public interface I18n {

    Locale getLocale();

    /**
     * Строка по ключу. Если задан хотя бы один аргумент, строка обрабатывается как {@link java.text.MessageFormat}
     * (апострофы удваиваются: {@code ''}); без аргументов возвращается как есть. Нет ключа — возвращается сам ключ.
     */
    String get(String key, Object... args);

    /** Заглушка без переводов: возвращает ключ. */
    static I18n passthrough() {
        return new I18n() {
            @Override
            public Locale getLocale() {
                return Locale.ENGLISH;
            }

            @Override
            public String get(String key, Object... args) {
                return key;
            }
        };
    }
}
