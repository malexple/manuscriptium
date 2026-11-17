package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MessagesResourcesTest {

    @Test
    void englishAndRussianBundlesHaveIdenticalKeysAndPlaceholders() throws IOException {
        Properties english = load("messages.properties");
        Properties russian = load("messages_ru.properties");

        assertThat(russian.keySet()).isEqualTo(english.keySet());
        assertThat(russian.stringPropertyNames()).allMatch(key -> placeholders(russian.getProperty(key))
                .equals(placeholders(english.getProperty(key))));
    }

    private static Properties load(String name) throws IOException {
        String path = "/ru/mcs/markdown/messages/" + name;
        try (InputStream input = MarkdownEditorModule.class.getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            Properties properties = new Properties();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static Set<String> placeholders(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\d+}").matcher(text);
        java.util.Set<String> result = new java.util.HashSet<>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }
}
