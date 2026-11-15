package ru.mcs.plugin.mdeditor.widget;

import java.util.Locale;

/** Неизменяемые prefix/suffix исходной главы и редактируемый фрагмент трансклюзии. */
public record TransclusionDocument(String prefix, String editable, String suffix) {

    public TransclusionDocument {
        prefix = prefix == null ? "" : prefix;
        editable = editable == null ? "" : editable;
        suffix = suffix == null ? "" : suffix;
    }

    public static TransclusionDocument whole(String source) {
        return new TransclusionDocument("", source, "");
    }

    /** Извлекает заголовок и его содержимое до следующего заголовка того же или более высокого уровня. */
    public static TransclusionDocument section(String source, String fragment) {
        String wanted = normalizeHeading(fragment);
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Empty transclusion heading");
        }
        int lineStart = 0;
        while (lineStart < source.length()) {
            int lineEnd = lineEnd(source, lineStart);
            Heading heading = heading(source, lineStart, lineEnd);
            if (heading != null && normalizeHeading(heading.title).equals(wanted)) {
                int end = nextLine(source, lineEnd);
                int scan = end;
                while (scan < source.length()) {
                    int candidateEnd = lineEnd(source, scan);
                    Heading next = heading(source, scan, candidateEnd);
                    if (next != null && next.level <= heading.level) {
                        end = scan;
                        break;
                    }
                    scan = nextLine(source, candidateEnd);
                    end = scan;
                }
                return new TransclusionDocument(source.substring(0, lineStart), source.substring(lineStart, end),
                        source.substring(end));
            }
            lineStart = nextLine(source, lineEnd);
        }
        throw new IllegalArgumentException("Heading not found: " + fragment);
    }

    public String merge(String edited) {
        return prefix + edited + suffix;
    }

    private static Heading heading(String source, int start, int end) {
        int p = start;
        while (p < end && p - start < 3 && source.charAt(p) == ' ') {
            p++;
        }
        int level = 0;
        while (p < end && source.charAt(p) == '#' && level < 6) {
            level++;
            p++;
        }
        if (level == 0 || p >= end || !Character.isWhitespace(source.charAt(p))) {
            return null;
        }
        while (p < end && Character.isWhitespace(source.charAt(p))) {
            p++;
        }
        int titleEnd = end;
        while (titleEnd > p && source.charAt(titleEnd - 1) == '#') {
            titleEnd--;
        }
        while (titleEnd > p && Character.isWhitespace(source.charAt(titleEnd - 1))) {
            titleEnd--;
        }
        return new Heading(level, source.substring(p, titleEnd));
    }

    private static String normalizeHeading(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}]+", "-");
        return normalized.replaceAll("^-|-$", "");
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

    private record Heading(int level, String title) {
    }
}
