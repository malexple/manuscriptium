package ru.mcs.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Безопасное форматирование: пробелы в конце строк, табуляция в отступах, пустые строки; содержимое блочных скаляров не меняется. */
final class YamlFormatter {

    private static final Pattern BLOCK_START = Pattern.compile(
            "^[ ]*(?:-[ ]+)*(?:(?:\"[^\"]*\"|'[^']*'|[^\\s#][^#]*?):[ ]+)?[|>][-+0-9]*[ ]*(?:#.*)?$");

    private YamlFormatter() {
    }

    static String format(String text, YamlStyle style) {
        if (text.isEmpty()) {
            return text;
        }
        boolean crlf = text.contains("\r\n");
        String source = crlf ? text.replace("\r\n", "\n") : text;
        boolean hadFinalNewline = source.endsWith("\n");
        String body = hadFinalNewline ? source.substring(0, source.length() - 1) : source;
        List<String> out = new ArrayList<>();
        int blockParent = -1;
        int blanks = 0;
        for (String line : body.split("\n", -1)) {
            if (blockParent >= 0 && (line.isBlank() || indentOf(line) > blockParent)) {
                out.add(line);
                continue;
            }
            blockParent = -1;
            String current = style.tabsToSpaces() ? replaceLeadingTabs(line, style.indentSize()) : line;
            if (style.trimTrailingWhitespace()) {
                current = current.stripTrailing();
            }
            if (current.isBlank()) {
                blanks++;
                if (blanks <= style.maxBlankLines()) {
                    out.add(style.trimTrailingWhitespace() ? "" : current);
                }
                continue;
            }
            blanks = 0;
            out.add(current);
            if (BLOCK_START.matcher(current).matches()) {
                blockParent = indentOf(current);
            }
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        String result = String.join("\n", out);
        if (!out.isEmpty() && (style.finalNewline() || hadFinalNewline)) {
            result += "\n";
        }
        return crlf ? result.replace("\n", "\r\n") : result;
    }

    private static String replaceLeadingTabs(String line, int indentSize) {
        int end = 0;
        while (end < line.length() && (line.charAt(end) == ' ' || line.charAt(end) == '\t')) {
            end++;
        }
        String leading = line.substring(0, end).replace("\t", " ".repeat(indentSize));
        return leading + line.substring(end);
    }

    private static int indentOf(String line) {
        int count = 0;
        while (count < line.length() && (line.charAt(count) == ' ' || line.charAt(count) == '\t')) {
            count++;
        }
        return count;
    }
}
