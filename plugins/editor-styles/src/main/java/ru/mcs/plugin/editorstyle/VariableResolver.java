package ru.mcs.plugin.editorstyle;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** var(--name, fallback) с вложенными переменными и защитой от циклов. */
final class VariableResolver {

    private VariableResolver() {
    }

    static String resolve(String value, Map<String, String> variables) {
        return resolve(value, variables, new HashSet<>());
    }

    private static String resolve(String value, Map<String, String> variables, Set<String> stack) {
        StringBuilder result = new StringBuilder(value.length());
        int cursor = 0;
        while (cursor < value.length()) {
            int start = indexOfVar(value, cursor);
            if (start < 0) {
                result.append(value, cursor, value.length());
                break;
            }
            result.append(value, cursor, start);
            int open = start + 3;
            int close = matchingParenthesis(value, open);
            if (close < 0) {
                throw new IllegalArgumentException("Unclosed var()");
            }
            String body = value.substring(open + 1, close);
            List<String> parts = CssText.split(body, ',');
            String name = parts.getFirst().trim();
            if (!name.startsWith("--")) {
                throw new IllegalArgumentException("Invalid variable name: " + name);
            }
            String fallback = parts.size() > 1 ? String.join(",", parts.subList(1, parts.size())).trim() : null;
            String replacement;
            if (variables.containsKey(name) && stack.add(name)) {
                replacement = resolve(variables.get(name), variables, stack);
                stack.remove(name);
            } else if (fallback != null) {
                replacement = resolve(fallback, variables, stack);
            } else {
                throw new IllegalArgumentException("Unresolved or cyclic variable: " + name);
            }
            result.append(replacement);
            cursor = close + 1;
        }
        return result.toString();
    }

    private static int indexOfVar(String value, int start) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.indexOf("var(", start);
    }

    private static int matchingParenthesis(String value, int open) {
        int depth = 1;
        for (int i = open + 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
