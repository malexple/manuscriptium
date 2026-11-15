package ru.mcs.plugin.mdeditor.widget;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Markdown/Obsidian image с необязательной Obsidian-шириной и исходным anchor-suffix. */
public record ImageSource(String raw, String alt, String path, Integer width, boolean wiki, String suffix) {

    private static final Pattern MARKDOWN = Pattern.compile(
            "!\\[([^]]*)]\\(\\s*(?:<([^>]+)>|([^\\s)]+))(?:\\s+[^)]*)?\\s*\\)", Pattern.DOTALL);
    private static final Pattern WIKI = Pattern.compile("!\\[\\[([^]|]+?)(?:\\|(\\d+))?]]", Pattern.DOTALL);
    private static final Pattern ALT_WIDTH = Pattern.compile("^(.*?)(?:\\|(\\d+))?$", Pattern.DOTALL);
    private static final Pattern SUFFIX = Pattern.compile(
            "\\s*\\{#(?:sec|fig|tbl):[A-Za-z0-9_.-]+}", Pattern.DOTALL);

    public ImageSource {
        suffix = suffix == null ? "" : suffix;
    }

    public static ImageSource parseBlock(String raw, int start, int end) {
        String source = raw.substring(start, end).strip();
        Matcher wiki = WIKI.matcher(source);
        if (wiki.find() && wiki.start() == 0 && validSuffix(source.substring(wiki.end()))) {
            return new ImageSource(source, wiki.group(1), decode(wiki.group(1).strip()),
                    integer(wiki.group(2)), true, source.substring(wiki.end()));
        }
        Matcher markdown = MARKDOWN.matcher(source);
        if (markdown.find() && markdown.start() == 0 && validSuffix(source.substring(markdown.end()))) {
            Matcher alt = ALT_WIDTH.matcher(markdown.group(1));
            alt.matches();
            String path = markdown.group(2) != null ? markdown.group(2) : markdown.group(3);
            return new ImageSource(source, alt.group(1), decode(path), integer(alt.group(2)), false,
                    source.substring(markdown.end()));
        }
        return null;
    }

    public String withWidth(int newWidth) {
        int value = Math.max(16, newWidth);
        if (wiki) {
            return "![[" + path + "|" + value + "]]" + suffix;
        }
        String baseAlt = alt == null ? "" : alt.replaceFirst("\\|\\d+$", "");
        String encoded = path.replace(" ", "%20");
        return "![" + baseAlt + "|" + value + "](" + encoded + ")" + suffix;
    }

    private static boolean validSuffix(String suffix) {
        return suffix.isEmpty() || SUFFIX.matcher(suffix).matches();
    }

    private static Integer integer(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Math.max(16, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String decode(String path) {
        try {
            return new URI(path).getPath();
        } catch (Exception ignored) {
            return path;
        }
    }
}
