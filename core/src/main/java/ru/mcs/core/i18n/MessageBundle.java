package ru.mcs.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Набор строк из .properties (UTF-8). Слои накладываются по порядку: {@code base}, {@code base_lang},
 * {@code base_lang_COUNTRY} — более специфичный перекрывает общий, поэтому недостающий перевод
 * автоматически берётся из английского (корневого) файла. Источник ресурсов абстрагирован через {@link Opener}.
 */
public final class MessageBundle {

    private static final System.Logger LOG = System.getLogger(MessageBundle.class.getName());

    /** Открывает ресурс по пути вида {@code a/b/messages.properties}; null, если ресурса нет. */
    @FunctionalInterface
    public interface Opener {
        InputStream open(String resourcePath) throws IOException;
    }

    private static final MessageBundle EMPTY = new MessageBundle(Map.of());

    private final Map<String, String> map;

    private MessageBundle(Map<String, String> map) {
        this.map = map;
    }

    public static MessageBundle empty() {
        return EMPTY;
    }

    /** @param base путь или имя с точками: {@code ru.mcs.core.i18n.messages} или {@code ru/mcs/core/i18n/messages} */
    public static MessageBundle load(String base, Locale locale, Opener opener) {
        String path = base.replace('.', '/');
        Map<String, String> merged = new HashMap<>();
        for (String suffix : suffixes(locale)) {
            merge(merged, path + suffix + ".properties", opener);
        }
        return new MessageBundle(merged);
    }

    /** Один файл без наложения слоёв (для проверки полноты переводов). */
    public static MessageBundle loadFile(String resourcePath, Opener opener) {
        Map<String, String> single = new HashMap<>();
        merge(single, resourcePath, opener);
        return new MessageBundle(single);
    }

    static List<String> suffixes(Locale locale) {
        String lang = locale.getLanguage();
        if (lang.isEmpty()) {
            return List.of("");
        }
        String country = locale.getCountry();
        if (country.isEmpty()) {
            return List.of("", "_" + lang);
        }
        return List.of("", "_" + lang, "_" + lang + "_" + country);
    }

    private static void merge(Map<String, String> target, String resource, Opener opener) {
        try (InputStream in = opener.open(resource)) {
            if (in == null) {
                return;
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            props.forEach((k, v) -> target.put(k.toString(), v.toString()));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot read messages: " + resource, e);
        }
    }

    public String get(String key) {
        return map.get(key);
    }

    public Set<String> keys() {
        return map.keySet();
    }

    public static String format(String pattern, Locale locale, Object... args) {
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(args);
    }
}
