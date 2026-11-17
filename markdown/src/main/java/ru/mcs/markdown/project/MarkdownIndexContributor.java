package ru.mcs.markdown.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import ru.mcs.spi.fs.FilePaths;
import ru.mcs.spi.project.IndexContributor;
import ru.mcs.spi.project.IndexedDocument;

/** Извлекает Markdown title, Front Matter, тело и search-attributes для универсального Project-индекса. */
public final class MarkdownIndexContributor implements IndexContributor {

    private static final Pattern TITLE = Pattern.compile("(?m)^title\\s*:\\s*(.*?)\\s*$");
    private static final Pattern HEADING = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final Pattern FRONT_MATTER_END = Pattern.compile("(?m)^(---|\\.\\.\\.)\\r?$");

    @Override
    public boolean supports(Path file) {
        Path name = file.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    @Override
    public IndexedDocument contribute(Path root, Path file, String content, long size, long modifiedMs) {
        String metadata = frontMatter(content);
        String title = yamlTitle(metadata);
        if (title == null) {
            Matcher heading = HEADING.matcher(content);
            title = heading.find() ? heading.group(1).trim() : null;
        }
        if (title == null || title.isBlank()) {
            String name = file.getFileName().toString();
            title = name.substring(0, name.length() - 3);
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        FrontMatterMetadata parsed = FrontMatterMetadata.parse(metadata);
        if (!parsed.tags().isEmpty()) {
            attributes.put("tags", String.join("\n", parsed.tags()));
        }
        attributes.put("draft", Boolean.toString(parsed.draft()));
        return new IndexedDocument(FilePaths.relativePath(root, file), title, metadata, content,
                body(content), size, modifiedMs, attributes);
    }

    static String body(String content) {
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        if (!text.startsWith("---\n") && !text.startsWith("---\r\n")) {
            return content;
        }
        int offset = text.startsWith("---\r\n") ? 5 : 4;
        Matcher end = FRONT_MATTER_END.matcher(text);
        if (!end.find(offset)) {
            return content;
        }
        int bodyStart = end.end();
        if (text.startsWith("\r\n", bodyStart)) {
            bodyStart += 2;
        } else if (text.startsWith("\n", bodyStart)) {
            bodyStart++;
        }
        return text.substring(bodyStart);
    }

    private static String frontMatter(String content) {
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        if (!text.startsWith("---\n") && !text.startsWith("---\r\n")) {
            return "";
        }
        int offset = text.startsWith("---\r\n") ? 5 : 4;
        Matcher end = FRONT_MATTER_END.matcher(text);
        if (!end.find(offset)) {
            return "";
        }
        return text.substring(offset, end.start());
    }

    private static String yamlTitle(String frontMatter) {
        if (frontMatter.isEmpty()) {
            return null;
        }
        Matcher matcher = TITLE.matcher(frontMatter);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    /** Невалидный YAML не мешает индексации текста; structured-фильтры получают безопасные значения. */
    private record FrontMatterMetadata(List<String> tags, boolean draft) {

        static FrontMatterMetadata parse(String frontMatter) {
            if (frontMatter == null || frontMatter.isBlank()) {
                return new FrontMatterMetadata(List.of(), false);
            }
            try {
                Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
                if (!(yaml.load(frontMatter) instanceof Map<?, ?> map)) {
                    return new FrontMatterMetadata(List.of(), false);
                }
                return new FrontMatterMetadata(tags(map.get("tags")), draft(map.get("draft")));
            } catch (RuntimeException e) {
                return new FrontMatterMetadata(List.of(), false);
            }
        }

        private static List<String> tags(Object value) {
            if (value instanceof Collection<?> collection) {
                List<String> result = new ArrayList<>();
                for (Object item : collection) {
                    if (item != null) {
                        result.add(item.toString());
                    }
                }
                return List.copyOf(result);
            }
            return value == null || value.toString().isBlank() ? List.of() : List.of(value.toString());
        }

        private static boolean draft(Object value) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            return value != null && Boolean.parseBoolean(value.toString());
        }
    }
}
