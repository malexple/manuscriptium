package ru.mcs.markdown.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Небольшой BibTeX reader: вложенные braces, quoted values и author/year/title без сторонней зависимости. */
final class BibTeXParser {

    private BibTeXParser() {
    }

    static List<CitationIndex.Entry> parse(String source) {
        List<CitationIndex.Entry> result = new ArrayList<>();
        int offset = 0;
        while ((offset = source.indexOf('@', offset)) >= 0) {
            int open = openingBrace(source, offset + 1);
            if (open < 0) {
                break;
            }
            int close = closingBrace(source, open);
            if (close < 0) {
                break;
            }
            String body = source.substring(open + 1, close);
            int comma = body.indexOf(',');
            if (comma > 0) {
                String key = body.substring(0, comma).trim();
                Map<String, String> fields = fields(body.substring(comma + 1));
                if (!key.isEmpty()) {
                    String author = first(fields, "author", "editor");
                    String year = first(fields, "year", "date");
                    result.add(new CitationIndex.Entry(key, clean(author), clean(fields.get("title")),
                            year == null ? "" : year.replaceAll("^.*?(\\d{4}).*$", "$1")));
                }
            }
            offset = close + 1;
        }
        return List.copyOf(result);
    }

    private static Map<String, String> fields(String source) {
        Map<String, String> result = new LinkedHashMap<>();
        int offset = 0;
        while (offset < source.length()) {
            while (offset < source.length() && (Character.isWhitespace(source.charAt(offset))
                    || source.charAt(offset) == ',')) {
                offset++;
            }
            int equals = source.indexOf('=', offset);
            if (equals < 0) {
                break;
            }
            String name = source.substring(offset, equals).trim().toLowerCase(Locale.ROOT);
            offset = equals + 1;
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                offset++;
            }
            if (offset >= source.length()) {
                break;
            }
            int start = offset;
            char opener = source.charAt(offset);
            if (opener == '{' || opener == '"') {
                char closer = opener == '{' ? '}' : '"';
                int depth = 0;
                do {
                    char ch = source.charAt(offset);
                    if (ch == '\\') {
                        offset += 2;
                        continue;
                    }
                    if (opener == '{' && ch == '{') {
                        depth++;
                    } else if (ch == closer && (opener != '{' || --depth == 0)) {
                        offset++;
                        break;
                    }
                    offset++;
                } while (offset < source.length());
            } else {
                while (offset < source.length() && source.charAt(offset) != ',') {
                    offset++;
                }
            }
            if (!name.isEmpty()) {
                result.put(name, source.substring(start, Math.min(offset, source.length())).trim());
            }
        }
        return result;
    }

    private static int openingBrace(String source, int start) {
        for (int i = start; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{' || ch == '(') {
                return i;
            }
            if (!Character.isLetter(ch) && !Character.isWhitespace(ch) && ch != '-') {
                return -1;
            }
        }
        return -1;
    }

    private static int closingBrace(String source, int open) {
        char opener = source.charAt(open);
        char closer = opener == '(' ? ')' : '}';
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '\\') {
                i++;
            } else if (ch == opener) {
                depth++;
            } else if (ch == closer && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String first(Map<String, String> fields, String... names) {
        for (String name : names) {
            if (fields.containsKey(name)) {
                return fields.get(name);
            }
        }
        return "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String result = value.strip();
        if (result.length() >= 2 && ((result.startsWith("{") && result.endsWith("}"))
                || (result.startsWith("\"") && result.endsWith("\"")))) {
            result = result.substring(1, result.length() - 1);
        }
        return result.replaceAll("[{}]", "").replaceAll("\\\\[a-zA-Z]+\\s*", "")
                .replaceAll("\\s+", " ").trim();
    }
}
