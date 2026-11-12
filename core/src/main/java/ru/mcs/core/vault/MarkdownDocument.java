package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Снимок Markdown-файла для индекса. Невалидный Front Matter не мешает индексации текста. */
record MarkdownDocument(String path, String title, String frontMatter, String content,
                        long size, long modifiedMs) {

    private static final Pattern TITLE = Pattern.compile("(?m)^title\\s*:\\s*(.*?)\\s*$");
    private static final Pattern HEADING = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final Pattern FRONT_MATTER_END = Pattern.compile("(?m)^(---|\\.\\.\\.)\\r?$");

    static MarkdownDocument read(Path root, Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String frontMatter = frontMatter(content);
        String title = yamlTitle(frontMatter);
        if (title == null) {
            Matcher heading = HEADING.matcher(content);
            title = heading.find() ? heading.group(1).trim() : null;
        }
        if (title == null || title.isBlank()) {
            String name = file.getFileName().toString();
            title = name.substring(0, name.length() - 3);
        }
        return new MarkdownDocument(VaultPaths.relativePath(root, file), title, frontMatter, content,
                Files.size(file), Files.getLastModifiedTime(file).toMillis());
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
}
