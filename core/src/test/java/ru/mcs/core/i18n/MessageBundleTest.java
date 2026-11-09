package ru.mcs.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageBundleTest {

    private static MessageBundle.Opener files(Map<String, String> content) {
        return path -> {
            String text = content.get(path);
            return text == null ? null : new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        };
    }

    private final Map<String, String> content = Map.of(
            "x/m.properties", "a=root-a\nb=root-b\n",
            "x/m_ru.properties", "b=ru-b\nc=ru-c\n",
            "x/m_ru_RU.properties", "c=ru-RU-c\n");

    @Test
    void moreSpecificLayersOverrideGeneralOnes() {
        MessageBundle ruRu = MessageBundle.load("x/m", Locale.of("ru", "RU"), files(content));
        assertThat(ruRu.get("a")).isEqualTo("root-a");
        assertThat(ruRu.get("b")).isEqualTo("ru-b");
        assertThat(ruRu.get("c")).isEqualTo("ru-RU-c");
    }

    @Test
    void missingTranslationFallsBackToRootAndUnknownLanguageUsesRoot() {
        MessageBundle ru = MessageBundle.load("x/m", Locale.of("ru"), files(content));
        assertThat(ru.get("a")).isEqualTo("root-a");
        assertThat(ru.get("c")).isEqualTo("ru-c");

        MessageBundle de = MessageBundle.load("x/m", Locale.GERMAN, files(content));
        assertThat(de.get("b")).isEqualTo("root-b");
        assertThat(de.get("c")).isNull();
    }

    @Test
    void dottedBaseNameIsConvertedToPath() {
        MessageBundle b = MessageBundle.load("x.m", Locale.ENGLISH, files(content));
        assertThat(b.get("a")).isEqualTo("root-a");
    }

    @Test
    void propertiesAreReadAsUtf8() {
        MessageBundle b = MessageBundle.load("u", Locale.ENGLISH, files(Map.of("u.properties", "k=Привет, мир → …\n")));
        assertThat(b.get("k")).isEqualTo("Привет, мир → …");
    }

    @Test
    void missingResourcesGiveEmptyBundle() {
        MessageBundle b = MessageBundle.load("nothing", Locale.ENGLISH, files(Map.of()));
        assertThat(b.keys()).isEmpty();
        assertThat(b.get("a")).isNull();
    }

    @Test
    void ioErrorsAreIgnored() {
        MessageBundle b = MessageBundle.load("x", Locale.ENGLISH, path -> {
            throw new IOException("disk");
        });
        assertThat(b.keys()).isEmpty();
    }

    @Test
    void loadFileReadsSingleLayerOnly() {
        MessageBundle ruOnly = MessageBundle.loadFile("x/m_ru.properties", files(content));
        assertThat(ruOnly.keys()).containsExactlyInAnyOrder("b", "c");
    }

    @Test
    void suffixesFollowLocaleSpecificity() {
        assertThat(MessageBundle.suffixes(Locale.ROOT)).containsExactly("");
        assertThat(MessageBundle.suffixes(Locale.of("ru"))).containsExactly("", "_ru");
        assertThat(MessageBundle.suffixes(Locale.of("ru", "RU"))).containsExactly("", "_ru", "_ru_RU");
    }

    @Test
    void formatAppliesMessageFormatOnlyWithArguments() {
        assertThat(MessageBundle.format("Hello {0}, you have {1}", Locale.ENGLISH, "Ann", "two"))
                .isEqualTo("Hello Ann, you have two");
        assertThat(MessageBundle.format("Don't {0}", Locale.ENGLISH)).isEqualTo("Don't {0}");
        assertThat(MessageBundle.format("It''s {0}", Locale.ENGLISH, "ok")).isEqualTo("It's ok");
        assertThat(List.of(MessageBundle.format("{0}", Locale.ENGLISH, List.of("a", "b")))).containsExactly("[a, b]");
    }
}
