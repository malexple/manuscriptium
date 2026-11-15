package ru.mcs.plugin.mdeditor.reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Автонумерация явных sec/fig/tbl-якорей и разрешение коротких ссылок #kind:name. */
public final class CrossReferenceIndex {

    public static final CrossReferenceIndex EMPTY = new CrossReferenceIndex(List.of());

    private static final Pattern ANCHOR = Pattern.compile("\\{#(sec|fig|tbl):([A-Za-z0-9_.-]+)}");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+.*");

    private final List<Entry> ordered;
    private final Map<String, Entry> entries;
    private final Map<Integer, Entry> byOffset;

    private CrossReferenceIndex(List<Entry> ordered) {
        this.ordered = List.copyOf(ordered);
        Map<String, Entry> byId = new HashMap<>();
        Map<Integer, Entry> offsets = new HashMap<>();
        for (Entry entry : ordered) {
            byId.putIfAbsent(entry.id(), entry);
            offsets.putIfAbsent(entry.sourceOffset(), entry);
        }
        entries = Map.copyOf(byId);
        byOffset = Map.copyOf(offsets);
    }

    public static CrossReferenceIndex scan(String source) {
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int section = 0;
        int figure = 0;
        int table = 0;
        int offset = 0;
        boolean frontMatter = false;
        char fence = 0;
        int fenceLength = 0;
        while (offset < source.length()) {
            int end = lineEnd(source, offset);
            String line = source.substring(offset, end);
            String trimmed = line.trim();
            if (offset == 0 && trimmed.equals("---")) {
                frontMatter = true;
                offset = nextLine(source, end);
                continue;
            }
            if (frontMatter) {
                if (trimmed.equals("---") || trimmed.equals("...")) {
                    frontMatter = false;
                }
                offset = nextLine(source, end);
                continue;
            }
            int marker = fenceLength(line);
            if (fence != 0) {
                if (marker >= fenceLength && fenceChar(line) == fence && fenceTail(line).isBlank()) {
                    fence = 0;
                    fenceLength = 0;
                }
                offset = nextLine(source, end);
                continue;
            }
            if (marker >= 3) {
                fence = fenceChar(line);
                fenceLength = marker;
                offset = nextLine(source, end);
                continue;
            }
            Matcher matcher = ANCHOR.matcher(line);
            while (matcher.find()) {
                String kind = matcher.group(1).toLowerCase(Locale.ROOT);
                String id = kind + ":" + matcher.group(2);
                if (!seen.add(id)) {
                    continue;
                }
                int number = switch (kind) {
                    case "sec" -> ++section;
                    case "fig" -> ++figure;
                    case "tbl" -> ++table;
                    default -> throw new IllegalArgumentException(kind);
                };
                int sourceOffset = offset + matcher.start();
                if (HEADING.matcher(line).matches()) {
                    sourceOffset = offset;
                }
                result.add(new Entry(id, kind, number, sourceOffset));
            }
            offset = nextLine(source, end);
        }
        return result.isEmpty() ? EMPTY : new CrossReferenceIndex(result);
    }

    public String reference(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : label(entry);
    }

    public String labelAt(int sourceOffset) {
        Entry entry = byOffset.get(sourceOffset);
        return entry == null ? null : label(entry) + ".";
    }

    public String labelInRange(int start, int end) {
        for (Entry entry : ordered) {
            if (entry.sourceOffset() >= start && entry.sourceOffset() <= end) {
                return label(entry);
            }
        }
        return null;
    }

    private static String label(Entry entry) {
        String kind = switch (entry.kind()) {
            case "sec" -> "Section";
            case "fig" -> "Figure";
            case "tbl" -> "Table";
            default -> entry.kind();
        };
        return kind + " " + entry.number();
    }

    private static int fenceLength(String line) {
        int p = 0;
        while (p < line.length() && p < 3 && line.charAt(p) == ' ') {
            p++;
        }
        if (p >= line.length() || (line.charAt(p) != '`' && line.charAt(p) != '~')) {
            return 0;
        }
        char marker = line.charAt(p);
        int start = p;
        while (p < line.length() && line.charAt(p) == marker) {
            p++;
        }
        return p - start;
    }

    private static char fenceChar(String line) {
        for (int p = 0; p < line.length() && p < 4; p++) {
            char ch = line.charAt(p);
            if (ch == '`' || ch == '~') {
                return ch;
            }
        }
        return 0;
    }

    private static String fenceTail(String line) {
        int p = 0;
        while (p < line.length() && p < 3 && line.charAt(p) == ' ') {
            p++;
        }
        if (p >= line.length()) {
            return "";
        }
        char marker = line.charAt(p);
        while (p < line.length() && line.charAt(p) == marker) {
            p++;
        }
        return line.substring(p);
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

    private record Entry(String id, String kind, int number, int sourceOffset) {
    }
}
