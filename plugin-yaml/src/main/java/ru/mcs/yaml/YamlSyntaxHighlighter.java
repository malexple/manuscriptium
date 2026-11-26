package ru.mcs.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import ru.mcs.editor.view.HighlightSpan;
import ru.mcs.editor.view.SyntaxHighlighter;

/** Построчный разбор YAML на токены; многострочные кавычки не отслеживаются, блочные скаляры — да. */
final class YamlSyntaxHighlighter implements SyntaxHighlighter {

    private static final Pattern NUMBER = Pattern.compile(
            "[-+]?(?:\\d[\\d_]*(?:\\.[\\d_]*)?(?:[eE][-+]?\\d+)?|\\.\\d+(?:[eE][-+]?\\d+)?|0x[0-9a-fA-F_]+|0o[0-7_]+)"
                    + "|[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN)");
    private static final Pattern BLOCK_INDICATOR = Pattern.compile("[|>][-+0-9]*[ \\t]*(?:#.*)?");
    private static final Set<String> CONSTANTS = Set.of("true", "True", "TRUE", "false", "False", "FALSE",
            "null", "Null", "NULL", "~");

    @Override
    public List<HighlightSpan> highlight(CharSequence text) {
        String source = text.toString();
        List<HighlightSpan> spans = new ArrayList<>();
        int position = 0;
        int blockParent = -1;
        while (true) {
            int end = source.indexOf('\n', position);
            boolean last = end < 0;
            if (last) {
                end = source.length();
            }
            blockParent = scanLine(source.substring(position, end), position, blockParent, spans);
            if (last) {
                break;
            }
            position = end + 1;
        }
        return spans;
    }

    private int scanLine(String line, int base, int blockParent, List<HighlightSpan> out) {
        int length = line.endsWith("\r") ? line.length() - 1 : line.length();
        int indent = 0;
        while (indent < length && isBlank(line.charAt(indent))) {
            indent++;
        }
        if (blockParent >= 0) {
            if (indent == length) {
                return blockParent;
            }
            if (indent > blockParent) {
                out.add(new HighlightSpan(base + indent, base + length, YamlTokens.STRING));
                return blockParent;
            }
        }
        if (indent == length) {
            return -1;
        }
        int i = indent;
        if (indent == 0 && line.charAt(0) == '%') {
            out.add(new HighlightSpan(base, base + length, YamlTokens.MARKER));
            return -1;
        }
        if (indent == 0 && isDocumentMarker(line, length)) {
            out.add(new HighlightSpan(base, base + 3, YamlTokens.MARKER));
            i = 3;
        }
        while (i < length && isBlank(line.charAt(i))) {
            i++;
        }
        while (i < length && line.charAt(i) == '-' && (i + 1 == length || isBlank(line.charAt(i + 1)))) {
            out.add(new HighlightSpan(base + i, base + i + 1, YamlTokens.PUNCT));
            i++;
            while (i < length && isBlank(line.charAt(i))) {
                i++;
            }
        }
        if (i >= length) {
            return -1;
        }
        int[] key = findKey(line, i, length);
        if (key != null) {
            out.add(new HighlightSpan(base + i, base + key[0], YamlTokens.KEY));
            out.add(new HighlightSpan(base + key[1], base + key[1] + 1, YamlTokens.PUNCT));
            i = key[1] + 1;
        }
        return scanValue(line, base, i, length, indent, out);
    }

    private int scanValue(String line, int base, int from, int length, int indent, List<HighlightSpan> out) {
        boolean block = false;
        int j = from;
        while (j < length) {
            char c = line.charAt(j);
            if (isBlank(c)) {
                j++;
            } else if (c == '#' && (j == 0 || isBlank(line.charAt(j - 1)))) {
                out.add(new HighlightSpan(base + j, base + length, YamlTokens.COMMENT));
                break;
            } else if (c == '"' || c == '\'') {
                int close = closingQuote(line, j, length);
                int end = close < 0 ? length : close + 1;
                out.add(new HighlightSpan(base + j, base + end, YamlTokens.STRING));
                j = end;
            } else if (c == '&' || c == '*' || c == '!') {
                int end = tokenEnd(line, j, length);
                out.add(new HighlightSpan(base + j, base + end, c == '!' ? YamlTokens.TAG : YamlTokens.ANCHOR));
                j = end;
            } else if ("[]{},".indexOf(c) >= 0) {
                out.add(new HighlightSpan(base + j, base + j + 1, YamlTokens.PUNCT));
                j++;
            } else if ((c == '|' || c == '>') && BLOCK_INDICATOR.matcher(line.substring(j, length)).matches()) {
                int end = j + 1;
                while (end < length && "-+0123456789".indexOf(line.charAt(end)) >= 0) {
                    end++;
                }
                out.add(new HighlightSpan(base + j, base + end, YamlTokens.PUNCT));
                block = true;
                j = end;
            } else {
                int end = tokenEnd(line, j, length);
                String word = line.substring(j, end);
                if (CONSTANTS.contains(word)) {
                    out.add(new HighlightSpan(base + j, base + end, YamlTokens.CONSTANT));
                } else if (NUMBER.matcher(word).matches()) {
                    out.add(new HighlightSpan(base + j, base + end, YamlTokens.NUMBER));
                }
                j = end;
            }
        }
        return block ? indent : -1;
    }

    /** Возвращает {конец текста ключа, позиция двоеточия} или null, если в строке нет ключа. */
    private static int[] findKey(String line, int start, int length) {
        char first = line.charAt(start);
        if (first == '"' || first == '\'') {
            int close = closingQuote(line, start, length);
            if (close < 0) {
                return null;
            }
            int k = close + 1;
            while (k < length && isBlank(line.charAt(k))) {
                k++;
            }
            return k < length && isKeyColon(line, k, length) ? new int[]{close + 1, k} : null;
        }
        if ("[{|>!&*#%@`".indexOf(first) >= 0) {
            return null;
        }
        for (int k = start; k < length; k++) {
            char c = line.charAt(k);
            if (c == '#' && k > start && isBlank(line.charAt(k - 1))) {
                return null;
            }
            if (isKeyColon(line, k, length)) {
                int end = k;
                while (end > start && isBlank(line.charAt(end - 1))) {
                    end--;
                }
                return end > start ? new int[]{end, k} : null;
            }
        }
        return null;
    }

    private static boolean isKeyColon(String line, int index, int length) {
        return line.charAt(index) == ':' && (index + 1 == length || isBlank(line.charAt(index + 1)));
    }

    private static int closingQuote(String line, int start, int length) {
        char quote = line.charAt(start);
        for (int j = start + 1; j < length; j++) {
            char c = line.charAt(j);
            if (quote == '"' && c == '\\') {
                j++;
            } else if (c == quote) {
                if (quote == '\'' && j + 1 < length && line.charAt(j + 1) == '\'') {
                    j++;
                } else {
                    return j;
                }
            }
        }
        return -1;
    }

    private static int tokenEnd(String line, int start, int length) {
        int end = start;
        while (end < length && !isBlank(line.charAt(end)) && ",]}".indexOf(line.charAt(end)) < 0) {
            end++;
        }
        return Math.max(end, start + 1);
    }

    private static boolean isDocumentMarker(String line, int length) {
        return (line.startsWith("---") || line.startsWith("...")) && (length == 3 || isBlank(line.charAt(3)));
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }
}
