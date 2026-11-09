package ru.mcs.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesResourcesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    @AfterEach
    void resetLocale() {
        Messages.init(Locale.ENGLISH);
    }

    @Test
    void russianDictionaryCoversExactlyTheEnglishKeys() {
        MessageBundle en = Messages.raw("");
        MessageBundle ru = Messages.raw("_ru");
        assertThat(en.keys()).isNotEmpty();
        assertThat(new TreeSet<>(ru.keys())).isEqualTo(new TreeSet<>(en.keys()));
    }

    @Test
    void translationsKeepTheSamePlaceholders() {
        MessageBundle en = Messages.raw("");
        MessageBundle ru = Messages.raw("_ru");
        for (String key : en.keys()) {
            assertThat(placeholders(ru.get(key))).as("placeholders of '%s'", key).isEqualTo(placeholders(en.get(key)));
        }
    }

    @Test
    void noValueIsBlank() {
        for (String suffix : new String[]{"", "_ru"}) {
            MessageBundle bundle = Messages.raw(suffix);
            for (String key : bundle.keys()) {
                assertThat(bundle.get(key)).as(key + suffix).isNotBlank();
            }
        }
    }

    @Test
    void localeSwitchChangesLookupAndMissingKeysAreMarked() {
        Messages.init(Locale.ENGLISH);
        assertThat(Messages.get("menu.file")).isEqualTo("File");
        Messages.init(Locale.of("ru"));
        assertThat(Messages.get("menu.file")).isEqualTo("Файл");
        assertThat(Messages.get("no.such.key")).isEqualTo("!no.such.key!");
        assertThat(Messages.get("unsaved.message", "a.md")).isEqualTo("Сохранить изменения в a.md?");
    }

    private static Set<String> placeholders(String text) {
        Set<String> result = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }
}
