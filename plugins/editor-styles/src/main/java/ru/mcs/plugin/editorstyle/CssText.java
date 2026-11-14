package ru.mcs.plugin.editorstyle;

import java.util.ArrayList;
import java.util.List;

/** Небольшие сканеры, учитывающие кавычки, комментарии и вложенные скобки. */
final class CssText {

    private CssText() {
    }

    static String stripComments(String css) {
        StringBuilder result = new StringBuilder(css.length());
        boolean comment = false;
        for (int i = 0; i < css.length(); i++) {
            char c = css.charAt(i);
            if (!comment && c == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') {
                comment = true;
                result.append("  ");
                i++;
            } else if (comment && c == '*' && i + 1 < css.length() && css.charAt(i + 1) == '/') {
                comment = false;
                result.append("  ");
                i++;
            } else {
                result.append(comment && c != '\n' && c != '\r' ? ' ' : c);
            }
        }
        return result.toString();
    }

    static List<String> split(String value, char delimiter) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int parentheses = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                parentheses++;
            } else if (c == ')' && parentheses > 0) {
                parentheses--;
            } else if (c == delimiter && parentheses == 0) {
                result.add(value.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(value.substring(start).trim());
        return result;
    }

    static int topLevelColon(String value) {
        int parentheses = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                parentheses++;
            } else if (c == ')' && parentheses > 0) {
                parentheses--;
            } else if (c == ':' && parentheses == 0) {
                return i;
            }
        }
        return -1;
    }

    static int lineAt(String css, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < css.length(); i++) {
            if (css.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    static boolean externalUrl(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("http://") || lower.contains("https://") || lower.contains("//");
    }
}
