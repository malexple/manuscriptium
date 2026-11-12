package ru.mcs.plugin.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Хирургическое обновление небольшого подмножества YAML front matter.
 * Неизвестные строки, тело, BOM и исходные разделители строк сохраняются.
 */
final class FrontMatter {

    static final String TITLE = "title";
    static final String TAGS = "tags";
    static final String DRAFT = "draft";
    private static final List<String> MANAGED_KEYS = List.of(TITLE, TAGS, DRAFT);

    record Values(String title, List<String> tags, boolean draft) {
        Values {
            title = Objects.requireNonNull(title);
            tags = List.copyOf(tags);
        }

        static Values empty() {
            return new Values("", List.of(), false);
        }
    }

    private record Line(String content, String separator) {
        void appendTo(StringBuilder out) {
            out.append(content).append(separator);
        }
    }

    private record Field(int start, int end, boolean dashList) {
    }

    private record ParsedScalar(String value, int next) {
    }

    private final String original;
    private final String bom;
    private final List<Line> lines;
    private final int closing;
    private final String newLine;
    private final Map<String, Field> fields;
    private final Values values;
    private final List<String> unknownLines;

    private FrontMatter(String original, String bom, List<Line> lines, int closing,
                        Map<String, Field> fields, Values values, List<String> unknownLines) {
        this.original = original;
        this.bom = bom;
        this.lines = lines;
        this.closing = closing;
        this.fields = fields;
        this.values = values;
        this.unknownLines = unknownLines;
        this.newLine = lines.stream().map(Line::separator).filter(s -> !s.isEmpty()).findFirst().orElse("\n");
    }

    static FrontMatter parse(String text) {
        Objects.requireNonNull(text);
        String bom = text.startsWith("\uFEFF") ? "\uFEFF" : "";
        List<Line> lines = splitLines(text.substring(bom.length()));
        if (lines.isEmpty() || !"---".equals(lines.get(0).content().trim())) {
            return new FrontMatter(text, bom, lines, -1, Map.of(), Values.empty(), List.of());
        }
        int closing = -1;
        for (int i = 1; i < lines.size(); i++) {
            String delimiter = lines.get(i).content().trim();
            if ("---".equals(delimiter) || "...".equals(delimiter)) {
                closing = i;
                break;
            }
        }
        if (closing < 0) {
            throw new IllegalArgumentException("Front matter opening delimiter has no closing --- or ...");
        }

        Map<String, Field> fields = new LinkedHashMap<>();
        Map<String, Object> parsed = new HashMap<>();
        for (int i = 1; i < closing; i++) {
            String content = lines.get(i).content();
            String key = topLevelKey(content);
            if (key == null || !MANAGED_KEYS.contains(key)) {
                continue;
            }
            if (fields.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate managed front matter key: " + key);
            }
            String raw = content.substring(content.indexOf(':') + 1).trim();
            int end = i + 1;
            boolean dashList = false;
            switch (key) {
                case TITLE -> parsed.put(key, scalar(raw));
                case DRAFT -> {
                    String draft = scalar(raw).toLowerCase(Locale.ROOT);
                    if (!"true".equals(draft) && !"false".equals(draft)) {
                        throw new IllegalArgumentException("draft must be true or false");
                    }
                    parsed.put(key, Boolean.valueOf(draft));
                }
                case TAGS -> {
                    List<String> tags = new ArrayList<>();
                    if (!raw.isEmpty()) {
                        tags.addAll(inlineList(raw));
                    } else {
                        dashList = true;
                        int cursor = i + 1;
                        while (cursor < closing && !isTopLevel(lines.get(cursor).content())) {
                            String item = lines.get(cursor).content();
                            if (!item.isBlank()) {
                                String trimmed = item.trim();
                                if (!trimmed.startsWith("-") || (trimmed.length() > 1
                                        && !Character.isWhitespace(trimmed.charAt(1)))) {
                                    throw new IllegalArgumentException("tags block accepts only dash-list items");
                                }
                                tags.add(scalar(trimmed.substring(1).trim()));
                            }
                            cursor++;
                        }
                        end = cursor;
                    }
                    parsed.put(key, List.copyOf(tags));
                }
                default -> throw new AssertionError(key);
            }
            fields.put(key, new Field(i, end, dashList));
            i = end - 1;
        }

        List<String> unknown = new ArrayList<>();
        for (int i = 1; i < closing;) {
            int index = i;
            Field managed = fields.values().stream().filter(field -> field.start() == index).findFirst().orElse(null);
            if (managed != null) {
                i = managed.end();
            } else {
                unknown.add(lines.get(i).content());
                i++;
            }
        }
        List<?> rawTags = (List<?>) parsed.getOrDefault(TAGS, List.of());
        Values values = new Values((String) parsed.getOrDefault(TITLE, ""),
                rawTags.stream().map(String.class::cast).toList(),
                (Boolean) parsed.getOrDefault(DRAFT, false));
        return new FrontMatter(text, bom, lines, closing, fields, values, List.copyOf(unknown));
    }

    Values values() {
        return values;
    }

    boolean present() {
        return closing >= 0;
    }

    String unknownYaml() {
        return String.join("\n", unknownLines);
    }

    String update(Values updated) {
        Objects.requireNonNull(updated);
        if (!present()) {
            List<Line> generated = generatedLines(updated, null);
            if (generated.isEmpty()) {
                return original;
            }
            StringBuilder out = new StringBuilder(original.length() + 64).append(bom);
            out.append("---").append(newLine);
            generated.forEach(line -> line.appendTo(out));
            out.append("---").append(newLine);
            lines.forEach(line -> line.appendTo(out));
            return out.toString();
        }

        Map<String, List<Line>> replacements = new HashMap<>();
        for (String key : MANAGED_KEYS) {
            replacements.put(key, generatedLines(key, updated, fields.get(key)));
        }
        Map<Integer, String> starts = new HashMap<>();
        fields.forEach((key, field) -> starts.put(field.start(), key));
        StringBuilder out = new StringBuilder(original.length() + 32).append(bom);
        int i = 0;
        while (i < lines.size()) {
            if (i == closing) {
                for (String key : MANAGED_KEYS) {
                    if (!fields.containsKey(key)) {
                        replacements.get(key).forEach(line -> line.appendTo(out));
                    }
                }
            }
            String managedKey = starts.get(i);
            if (managedKey != null) {
                replacements.get(managedKey).forEach(line -> line.appendTo(out));
                i = fields.get(managedKey).end();
            } else {
                lines.get(i).appendTo(out);
                i++;
            }
        }
        return out.toString();
    }

    private List<Line> generatedLines(Values updated, Field existing) {
        List<Line> result = new ArrayList<>();
        for (String key : MANAGED_KEYS) {
            result.addAll(generatedLines(key, updated, existing == null ? null : fields.get(key)));
        }
        return result;
    }

    private List<Line> generatedLines(String key, Values value, Field existing) {
        return switch (key) {
            case TITLE -> value.title().isEmpty() ? List.of()
                    : List.of(line(TITLE + ": " + quote(value.title())));
            case TAGS -> {
                if (value.tags().isEmpty()) {
                    yield List.of();
                }
                if (existing != null && existing.dashList()) {
                    List<Line> result = new ArrayList<>();
                    result.add(line(TAGS + ":"));
                    value.tags().forEach(tag -> result.add(line("  - " + quote(tag))));
                    yield result;
                }
                yield List.of(line(TAGS + ": [" + String.join(", ",
                        value.tags().stream().map(FrontMatter::quote).toList()) + "]"));
            }
            case DRAFT -> !value.draft() && existing == null ? List.of()
                    : List.of(line(DRAFT + ": " + value.draft()));
            default -> throw new AssertionError(key);
        };
    }

    private Line line(String content) {
        return new Line(content, newLine);
    }

    private static List<Line> splitLines(String text) {
        List<Line> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) {
                result.add(new Line(text.substring(start), ""));
                break;
            }
            int contentEnd = newline > start && text.charAt(newline - 1) == '\r' ? newline - 1 : newline;
            result.add(new Line(text.substring(start, contentEnd), newline - contentEnd == 1 ? "\r\n" : "\n"));
            start = newline + 1;
        }
        return result;
    }

    private static String topLevelKey(String line) {
        if (!isTopLevel(line)) {
            return null;
        }
        int colon = line.indexOf(':');
        return colon < 0 ? null : line.substring(0, colon).trim();
    }

    private static boolean isTopLevel(String line) {
        return !line.isEmpty() && !Character.isWhitespace(line.charAt(0));
    }

    private static List<String> inlineList(String raw) {
        String text = raw.trim();
        if (text.length() < 2 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            throw new IllegalArgumentException("tags must be an inline list or a dash-list");
        }
        List<String> result = new ArrayList<>();
        int cursor = 1;
        while (true) {
            cursor = skipWhitespace(text, cursor);
            if (cursor == text.length() - 1 && text.charAt(cursor) == ']') {
                return List.copyOf(result);
            }
            if (cursor >= text.length() - 1) {
                throw new IllegalArgumentException("Invalid inline tags list");
            }
            String item;
            char first = text.charAt(cursor);
            if (first == '\'' || first == '"') {
                ParsedScalar parsed = quoted(text, cursor);
                item = parsed.value();
                cursor = skipWhitespace(text, parsed.next());
            } else {
                int start = cursor;
                while (cursor < text.length() && text.charAt(cursor) != ',' && text.charAt(cursor) != ']') {
                    cursor++;
                }
                item = text.substring(start, cursor).trim();
            }
            result.add(item);
            if (cursor < text.length() && text.charAt(cursor) == ',') {
                cursor++;
                continue;
            }
            cursor = skipWhitespace(text, cursor);
            if (cursor == text.length() - 1 && text.charAt(cursor) == ']') {
                return List.copyOf(result);
            }
            throw new IllegalArgumentException("Invalid inline tags list");
        }
    }

    private static String scalar(String raw) {
        String text = raw.trim();
        if (text.equals("|") || text.equals(">") || text.startsWith("| ") || text.startsWith("> ")) {
            throw new IllegalArgumentException("Block scalars are not supported for managed properties");
        }
        if (!text.isEmpty() && (text.charAt(0) == '\'' || text.charAt(0) == '"')) {
            ParsedScalar parsed = quoted(text, 0);
            if (!text.substring(parsed.next()).isBlank()) {
                throw new IllegalArgumentException("Unexpected text after quoted scalar");
            }
            return parsed.value();
        }
        return text;
    }

    private static ParsedScalar quoted(String text, int start) {
        char quote = text.charAt(start);
        StringBuilder value = new StringBuilder();
        for (int i = start + 1; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == quote) {
                if (quote == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    value.append('\'');
                    i++;
                    continue;
                }
                return new ParsedScalar(value.toString(), i + 1);
            }
            if (quote == '"' && current == '\\') {
                if (++i >= text.length()) {
                    break;
                }
                char escaped = text.charAt(i);
                switch (escaped) {
                    case '0' -> value.append('\0');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case '\\' -> value.append('\\');
                    case '"' -> value.append('"');
                    case '/' -> value.append('/');
                    case 'u' -> {
                        if (i + 4 >= text.length()) {
                            throw new IllegalArgumentException("Incomplete Unicode escape");
                        }
                        try {
                            value.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid Unicode escape", e);
                        }
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Unsupported escape: \\" + escaped);
                }
            } else {
                value.append(current);
            }
        }
        throw new IllegalArgumentException("Unclosed quoted scalar");
    }

    private static int skipWhitespace(String text, int cursor) {
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isISOControl(current)) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
                    } else {
                        out.append(current);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
