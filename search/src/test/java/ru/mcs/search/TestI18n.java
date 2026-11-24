package ru.mcs.search;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import ru.mcs.spi.i18n.I18n;

/** Английские строки модуля поиска без зависимости от локали машины, на которой идут тесты. */
final class TestI18n implements I18n {

    private final Properties messages = new Properties();

    TestI18n() {
        try (var in = TestI18n.class.getResourceAsStream("messages/messages.properties")) {
            if (in == null) {
                throw new IllegalStateException("messages.properties of the search module is missing");
            }
            messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Locale getLocale() {
        return Locale.ENGLISH;
    }

    @Override
    public String get(String key, Object... args) {
        String pattern = messages.getProperty(key, key);
        return args == null || args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
