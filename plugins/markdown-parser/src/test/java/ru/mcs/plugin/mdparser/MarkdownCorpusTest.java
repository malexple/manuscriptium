package ru.mcs.plugin.mdparser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.editor.BlockSpan;

class MarkdownCorpusTest {

    private final FlexmarkMarkdownParser parser = new FlexmarkMarkdownParser();

    @Test
    void commonMarkAndObsidianCorpusMatchesExpectedBlockSpans() throws IOException {
        for (String name : List.of("commonmark-headings", "commonmark-inline", "commonmark-blocks",
                "obsidian-extensions")) {
            String markdown = resource(name + ".md");
            String expected = resource(name + ".spans");

            assertThat(serialize(parser.parse(markdown, 1).spans())).as(name).isEqualTo(expected);
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = MarkdownCorpusTest.class.getResourceAsStream("/corpus/" + name)) {
            assertThat(input).as("corpus resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
        }
    }

    private static String serialize(List<BlockSpan> spans) {
        StringBuilder result = new StringBuilder();
        for (BlockSpan span : spans) {
            StringBuilder attributes = new StringBuilder();
            span.attributes().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (!attributes.isEmpty()) {
                    attributes.append(';');
                }
                attributes.append(entry.getKey()).append('=').append(entry.getValue());
            });
            result.append(span.kind()).append('|').append(span.start()).append('|').append(span.end())
                    .append('|').append(span.depth()).append('|').append(attributes).append('\n');
        }
        return result.toString();
    }
}
