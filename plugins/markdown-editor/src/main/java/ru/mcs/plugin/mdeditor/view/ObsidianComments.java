package ru.mcs.plugin.mdeditor.view;

import java.util.ArrayList;
import java.util.List;

/** Точные диапазоны %%…%% вне front matter и fenced code. */
public final class ObsidianComments {

    private ObsidianComments() {
    }

    public static List<Comment> parse(String source) {
        List<Comment> result = new ArrayList<>();
        int lineStart = 0;
        boolean frontMatter = source.startsWith("---\n") || source.startsWith("---\r\n");
        char fence = 0;
        int fenceLength = 0;
        while (lineStart < source.length()) {
            int end = lineEnd(source, lineStart);
            String line = source.substring(lineStart, end);
            if (frontMatter) {
                if (lineStart > 0 && (line.equals("---") || line.equals("..."))) {
                    frontMatter = false;
                }
                lineStart = nextLine(source, end);
                continue;
            }
            String stripped = line.stripLeading();
            if (stripped.length() >= 3 && (stripped.charAt(0) == '`' || stripped.charAt(0) == '~')) {
                char marker = stripped.charAt(0);
                int length = 0;
                while (length < stripped.length() && stripped.charAt(length) == marker) {
                    length++;
                }
                if (length >= 3) {
                    if (fence == 0) {
                        fence = marker;
                        fenceLength = length;
                    } else if (marker == fence && length >= fenceLength && stripped.substring(length).isBlank()) {
                        fence = 0;
                    }
                    lineStart = nextLine(source, end);
                    continue;
                }
            }
            if (fence == 0) {
                int cursor = lineStart;
                while (cursor < end) {
                    int open = source.indexOf("%%", cursor);
                    if (open < 0 || open >= end) {
                        break;
                    }
                    int close = source.indexOf("%%", open + 2);
                    if (close < 0) {
                        break;
                    }
                    result.add(new Comment(open, close + 2, source.substring(open + 2, close).strip()));
                    cursor = close + 2;
                }
            }
            lineStart = nextLine(source, end);
        }
        return List.copyOf(result);
    }

    private static int lineEnd(String source, int start) {
        int end = start;
        while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
            end++;
        }
        return end;
    }

    private static int nextLine(String source, int end) {
        if (end < source.length() && source.charAt(end) == '\r' && end + 1 < source.length()
                && source.charAt(end + 1) == '\n') {
            return end + 2;
        }
        return end < source.length() ? end + 1 : source.length();
    }

    public record Comment(int start, int end, String text) {
    }
}
