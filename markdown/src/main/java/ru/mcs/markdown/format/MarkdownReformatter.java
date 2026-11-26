package ru.mcs.markdown.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Форматирует Markdown-текст по {@link CodeStyle}; не знает про редактор и не меняет код, формулы и front matter. */
public final class MarkdownReformatter {

    private static final Pattern HEADING = Pattern.compile("^ {0,3}#{1,6}(?:[ \\t].*)?$");
    private static final Pattern HR = Pattern.compile("^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$");
    private static final Pattern FENCE = Pattern.compile("^([ \\t]*)(`{3,}|~{3,}).*$");
    private static final Pattern SEPARATOR = Pattern.compile(
            "^[ \\t]*\\|?[ \\t]*:?-+:?[ \\t]*(?:\\|[ \\t]*:?-+:?[ \\t]*)*\\|?[ \\t]*$");
    private static final Pattern QUOTE = Pattern.compile("^( {0,3}(?:> ?)+)(.*)$");
    private static final Pattern LIST = Pattern.compile("^([ \\t]*(?:[-*+]|\\d{1,9}[.)])[ \\t]+)(.*)$");
    private static final Pattern UNDERLINE = Pattern.compile("^ {0,3}(?:=+|-+)[ \\t]*$");
    private static final Pattern DEFINITION = Pattern.compile("^\\[[^\\]]+\\]:.*$");
    private static final Pattern ATOMIC = Pattern.compile(
            "!?\\[\\[[^\\]\\n]*\\]\\]|\\$[^$\\n]+\\$|`[^`\\n]+`|<[^>\\n]+>|\\[[ xX]\\]|!?\\[[^\\]\\n]*\\]\\([^)\\n]*\\)");
    private static final Pattern BLOCK_START = Pattern.compile("^(?:[-+*>]|\\d{1,9}[.)]|#{1,6})$");

    private enum Kind { BLANK, TEXT, HEADING, HR, FENCE, MATH, TABLE, VERBATIM }

    private enum Alignment { NONE, LEFT, CENTER, RIGHT }

    private record Block(Kind kind, List<String> lines, boolean isolated) {
    }

    private static final class Unit {
        private String quote = "";
        private String marker = "";
        private final List<String> contents = new ArrayList<>();
        private boolean locked;
        private int end;
    }

    private MarkdownReformatter() {
    }

    public static String format(String text, CodeStyle style) {
        if (text.isEmpty()) {
            return text;
        }
        boolean crlf = text.contains("\r\n");
        String source = crlf ? text.replace("\r\n", "\n") : text;
        boolean finalNewline = source.endsWith("\n");
        String body = finalNewline ? source.substring(0, source.length() - 1) : source;
        List<String> lines = new ArrayList<>(Arrays.asList(body.split("\n", -1)));
        List<String> out = assemble(split(lines), style);
        String result = String.join("\n", out);
        if (finalNewline && !out.isEmpty()) {
            result += "\n";
        }
        return crlf ? result.replace("\n", "\r\n") : result;
    }

    // ---------- разбиение на блоки ----------

    private static List<Block> split(List<String> lines) {
        List<Block> blocks = new ArrayList<>();
        List<String> text = new ArrayList<>();
        int count = lines.size();
        int i = 0;
        int front = frontMatterEnd(lines);
        if (front >= 0) {
            blocks.add(new Block(Kind.VERBATIM, new ArrayList<>(lines.subList(0, front + 1)), false));
            i = front + 1;
        }
        while (i < count) {
            String line = lines.get(i);
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                flush(blocks, text);
                blocks.add(new Block(Kind.BLANK, List.of(""), false));
                i++;
                continue;
            }
            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                flush(blocks, text);
                int end = fenceEnd(lines, i, fence.group(2));
                blocks.add(new Block(Kind.FENCE, new ArrayList<>(lines.subList(i, end + 1)),
                        fence.group(1).isEmpty()));
                i = end + 1;
                continue;
            }
            if (trimmed.startsWith("$$")) {
                flush(blocks, text);
                int end = trimmed.length() > 2 && trimmed.endsWith("$$") ? i : closing(lines, i + 1, "$$");
                blocks.add(new Block(Kind.MATH, new ArrayList<>(lines.subList(i, end + 1)),
                        !Character.isWhitespace(line.charAt(0))));
                i = end + 1;
                continue;
            }
            if (trimmed.startsWith("<!--") && !trimmed.contains("-->")) {
                flush(blocks, text);
                int end = closing(lines, i + 1, "-->");
                blocks.add(new Block(Kind.VERBATIM, new ArrayList<>(lines.subList(i, end + 1)), false));
                i = end + 1;
                continue;
            }
            if (HEADING.matcher(line).matches()) {
                flush(blocks, text);
                blocks.add(new Block(Kind.HEADING, List.of(line), true));
                i++;
                continue;
            }
            boolean setext = !text.isEmpty() && trimmed.matches("-+");
            if (!setext && HR.matcher(line).matches()) {
                flush(blocks, text);
                blocks.add(new Block(Kind.HR, List.of(line), true));
                i++;
                continue;
            }
            if (text.isEmpty() && isTableStart(lines, i)) {
                int end = i + 2;
                while (end < count && !lines.get(end).isBlank() && lines.get(end).contains("|")) {
                    end++;
                }
                blocks.add(new Block(Kind.TABLE, new ArrayList<>(lines.subList(i, end)), true));
                i = end;
                continue;
            }
            text.add(line);
            i++;
        }
        flush(blocks, text);
        return blocks;
    }

    private static void flush(List<Block> blocks, List<String> text) {
        if (!text.isEmpty()) {
            blocks.add(new Block(Kind.TEXT, new ArrayList<>(text), false));
            text.clear();
        }
    }

    private static int frontMatterEnd(List<String> lines) {
        if (lines.size() < 2 || !lines.get(0).strip().equals("---")) {
            return -1;
        }
        for (int j = 1; j < lines.size(); j++) {
            String line = lines.get(j).strip();
            if (line.equals("---") || line.equals("...")) {
                return j;
            }
        }
        return -1;
    }

    private static int fenceEnd(List<String> lines, int start, String marker) {
        char symbol = marker.charAt(0);
        for (int j = start + 1; j < lines.size(); j++) {
            String line = lines.get(j).strip();
            if (line.length() >= marker.length() && line.chars().allMatch(c -> c == symbol)) {
                return j;
            }
        }
        return lines.size() - 1;
    }

    private static int closing(List<String> lines, int from, String token) {
        for (int j = from; j < lines.size(); j++) {
            if (lines.get(j).contains(token)) {
                return j;
            }
        }
        return lines.size() - 1;
    }

    private static boolean isTableStart(List<String> lines, int i) {
        if (i + 1 >= lines.size()) {
            return false;
        }
        String header = lines.get(i);
        String separator = lines.get(i + 1);
        return header.contains("|") && separator.contains("|") && SEPARATOR.matcher(separator).matches()
                && cells(header).size() == cells(separator).size();
    }

    // ---------- сборка ----------

    private static List<String> assemble(List<Block> blocks, CodeStyle style) {
        List<String> out = new ArrayList<>();
        int blanks = 0;
        boolean previousIsolated = false;
        for (Block block : blocks) {
            if (block.kind() == Kind.BLANK) {
                blanks++;
                continue;
            }
            int emit = Math.min(blanks, style.maxBlankLines());
            if (style.blankLinesAround() > 0 && (block.isolated() || previousIsolated)) {
                emit = Math.max(emit, style.blankLinesAround());
            }
            if (out.isEmpty()) {
                emit = 0;
            }
            for (int k = 0; k < emit; k++) {
                out.add("");
            }
            out.addAll(render(block, style));
            previousIsolated = block.isolated();
            blanks = 0;
        }
        return out;
    }

    private static List<String> render(Block block, CodeStyle style) {
        return switch (block.kind()) {
            case TEXT -> formatText(block.lines(), style);
            case HEADING, HR -> block.lines().stream().map(String::stripTrailing).toList();
            case TABLE -> style.formatTables() ? formatTable(block.lines())
                    : block.lines().stream().map(String::stripTrailing).toList();
            default -> block.lines();
        };
    }

    // ---------- текст и перенос ----------

    private static List<String> formatText(List<String> lines, CodeStyle style) {
        List<String> cleaned = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean hardBreak = i < lines.size() - 1 && line.endsWith("  ") && !line.isBlank();
            cleaned.add(hardBreak ? line : line.stripTrailing());
        }
        if (style.hardWrap() == 0) {
            return cleaned;
        }
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < cleaned.size()) {
            Unit unit = readUnit(cleaned, i, style);
            emit(unit, cleaned, i, style, out);
            i = unit.end;
        }
        return out;
    }

    private static Unit readUnit(List<String> lines, int start, CodeStyle style) {
        Unit unit = new Unit();
        String first = lines.get(start);
        String rest = first;
        Matcher quote = QUOTE.matcher(first);
        if (quote.matches()) {
            unit.quote = quote.group(1);
            rest = quote.group(2);
        }
        String content;
        Matcher list = LIST.matcher(rest);
        if (list.matches()) {
            unit.marker = list.group(1);
            content = list.group(2);
        } else {
            int lead = leadingSpaces(rest);
            unit.marker = rest.substring(0, lead);
            content = rest.substring(lead);
            unit.locked = lead >= 4;
        }
        unit.contents.add(content);
        unit.locked |= lockedContent(content) || (!unit.quote.isEmpty() && !style.wrapQuotes());
        boolean hardBreak = isHardBreak(first);
        int j = start + 1;
        while (!hardBreak && j < lines.size()) {
            String next = lines.get(j);
            String nextQuote = "";
            String nextRest = next;
            Matcher nextMatch = QUOTE.matcher(next);
            if (nextMatch.matches()) {
                nextQuote = nextMatch.group(1);
                nextRest = nextMatch.group(2);
            }
            if (!nextQuote.replace(" ", "").equals(unit.quote.replace(" ", ""))
                    || nextRest.isBlank() || LIST.matcher(nextRest).matches()) {
                break;
            }
            if (UNDERLINE.matcher(next).matches()) {
                unit.locked = true;
                j++;
                break;
            }
            String stripped = nextRest.strip();
            unit.contents.add(stripped);
            unit.locked |= lockedContent(stripped);
            hardBreak = isHardBreak(next);
            j++;
        }
        unit.locked |= hardBreak;
        unit.end = j;
        return unit;
    }

    private static void emit(Unit unit, List<String> lines, int start, CodeStyle style, List<String> out) {
        int width = style.hardWrap();
        String firstPrefix = unit.quote + unit.marker;
        String continuation = unit.quote + " ".repeat(unit.marker.length());
        if (unit.locked) {
            out.addAll(lines.subList(start, unit.end));
        } else if (style.keepLineBreaks()) {
            for (int index = 0; index < unit.contents.size(); index++) {
                String original = lines.get(start + index);
                if (original.length() <= width) {
                    out.add(original);
                } else {
                    out.addAll(wrap(unit.contents.get(index), index == 0 ? firstPrefix : continuation,
                            continuation, width));
                }
            }
        } else {
            out.addAll(wrap(String.join(" ", unit.contents), firstPrefix, continuation, width));
        }
    }

    private static List<String> wrap(String content, String firstPrefix, String continuation, int width) {
        List<String> tokens = tokenize(content);
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder(firstPrefix);
        boolean empty = true;
        for (String token : tokens) {
            if (empty) {
                current.append(token);
                empty = false;
            } else if (current.length() + 1 + token.length() <= width || BLOCK_START.matcher(token).matches()) {
                current.append(' ').append(token);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(continuation).append(token);
            }
        }
        lines.add(empty ? firstPrefix.stripTrailing() : current.toString());
        return lines;
    }

    private static List<String> tokenize(String content) {
        boolean[] atomic = new boolean[content.length()];
        Matcher matcher = ATOMIC.matcher(content);
        while (matcher.find()) {
            Arrays.fill(atomic, matcher.start(), matcher.end(), true);
        }
        List<String> tokens = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == ' ' && !atomic[i]) {
                if (start >= 0) {
                    tokens.add(content.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            tokens.add(content.substring(start));
        }
        return tokens;
    }

    private static boolean lockedContent(String content) {
        return content.startsWith("<") || content.startsWith("|") || content.startsWith("%%")
                || content.startsWith("$$") || DEFINITION.matcher(content).matches();
    }

    private static boolean isHardBreak(String line) {
        return line.endsWith("  ") || line.endsWith("\\");
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    // ---------- таблицы ----------

    private static List<String> formatTable(List<String> rows) {
        String indent = " ".repeat(Math.min(3, leadingSpaces(rows.get(0))));
        List<List<String>> parsed = new ArrayList<>();
        int columns = 0;
        for (String row : rows) {
            List<String> cells = cells(row);
            parsed.add(cells);
            columns = Math.max(columns, cells.size());
        }
        Alignment[] alignment = new Alignment[columns];
        Arrays.fill(alignment, Alignment.NONE);
        List<String> separator = parsed.get(1);
        for (int c = 0; c < separator.size(); c++) {
            String cell = separator.get(c);
            boolean left = cell.startsWith(":");
            boolean right = cell.endsWith(":");
            alignment[c] = left && right ? Alignment.CENTER : left ? Alignment.LEFT
                    : right ? Alignment.RIGHT : Alignment.NONE;
        }
        int[] width = new int[columns];
        Arrays.fill(width, 3);
        for (int r = 0; r < parsed.size(); r++) {
            if (r == 1) {
                continue;
            }
            for (int c = 0; c < parsed.get(r).size(); c++) {
                String cell = parsed.get(r).get(c);
                width[c] = Math.max(width[c], cell.codePointCount(0, cell.length()));
            }
        }
        List<String> out = new ArrayList<>();
        for (int r = 0; r < parsed.size(); r++) {
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                cells.add(r == 1 ? dashes(width[c], alignment[c])
                        : pad(c < parsed.get(r).size() ? parsed.get(r).get(c) : "", width[c], alignment[c]));
            }
            out.add(indent + "| " + String.join(" | ", cells) + " |");
        }
        return out;
    }

    private static String dashes(int width, Alignment alignment) {
        return switch (alignment) {
            case LEFT -> ":" + "-".repeat(width - 1);
            case RIGHT -> "-".repeat(width - 1) + ":";
            case CENTER -> ":" + "-".repeat(width - 2) + ":";
            default -> "-".repeat(width);
        };
    }

    private static String pad(String cell, int width, Alignment alignment) {
        int gap = width - cell.codePointCount(0, cell.length());
        return switch (alignment) {
            case RIGHT -> " ".repeat(gap) + cell;
            case CENTER -> " ".repeat(gap / 2) + cell + " ".repeat(gap - gap / 2);
            default -> cell + " ".repeat(gap);
        };
    }

    private static List<String> cells(String row) {
        String text = row.strip();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|") && !text.endsWith("\\|")) {
            text = text.substring(0, text.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char symbol = text.charAt(i);
            if (symbol == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '|') {
                current.append("\\|");
                i++;
            } else if (symbol == '|') {
                cells.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(symbol);
            }
        }
        cells.add(current.toString().strip());
        return cells;
    }
}
