package ru.mcs.core.i18n;

import java.util.Locale;

/**
 * Строки самого приложения. Язык задаётся один раз при запуске ({@link #init}); до этого — английский,
 * поэтому тесты не зависят от языка системы. Отсутствующий ключ возвращается как {@code !ключ!}.
 */
public final class Messages {

    static final String BASE = "ru/mcs/core/i18n/messages";

    private static volatile Locale locale = Locale.ENGLISH;
    private static volatile MessageBundle bundle = load(Locale.ENGLISH);

    private Messages() {
    }

    public static void init(Locale newLocale) {
        bundle = load(newLocale);
        locale = newLocale;
    }

    public static Locale locale() {
        return locale;
    }

    public static String get(String key, Object... args) {
        String pattern = bundle.get(key);
        if (pattern == null) {
            return "!" + key + "!";
        }
        return MessageBundle.format(pattern, locale, args);
    }

    static MessageBundle load(Locale forLocale) {
        return MessageBundle.load(BASE, forLocale, Messages::open);
    }

    /** Один файл словаря: {@code ""} — английский, {@code "_ru"} — русский. */
    static MessageBundle raw(String suffix) {
        return MessageBundle.loadFile(BASE + suffix + ".properties", Messages::open);
    }

    private static java.io.InputStream open(String path) {
        return Messages.class.getResourceAsStream("/" + path);
    }
}
