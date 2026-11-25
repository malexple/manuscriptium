package ru.mcs.markdown.render;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.mcs.editor.view.HighlightSpan;
import ru.mcs.editor.view.SyntaxHighlighter;

/**
 * Быстрая построчная подсветка Markdown для вкладки «Исходник». Блоки (front matter, ограждённый код, формулы,
 * HTML-комментарии) отслеживаются между строками; внутри строки токены ищутся слева направо без пересечений.
 * Не строит AST и терпима к незаконченной разметке, поэтому подходит для текста, который сейчас набирают.
 */
public final class MarkdownSyntaxHighlighter implements SyntaxHighlighter {

    private record Inline(String token, Pattern pattern) {
    }

    private static final String ESCAPE = null;
    private static final String LINK = "#link";

    private static final List<Inline> INLINE = List.of(
            new Inline(ESCAPE, Pattern.compile("\\\\[\\\\`*_{}\\[\\]()#+\\-.!|~$<>]")),
            new Inline(MarkdownTokens.CODE, Pattern.compile("(?<!`)(`+)(?!`).+?(?<!`)\\1(?!`)")),
            new Inline(MarkdownTokens.COMMENT, Pattern.compile("<!--.*?-->|%%.*?%%")),
            new Inline(MarkdownTokens.LINK_URL, Pattern.compile("<https?://[^>\\s]+>")),
            new Inline(MarkdownTokens.HTML, Pattern.compile("</?[A-Za-z][^<>\\n]*>")),
            new Inline(MarkdownTokens.MATH, Pattern.compile("\\$(?=[^\\s$])[^$\\n]*?(?<=[^\\s$])\\$(?!\\d)")),
            new Inline(MarkdownTokens.WIKILINK, Pattern.compile("!?\\[\\[[^\\]\\n]+\\]\\]")),
            new Inline(MarkdownTokens.FOOTNOTE, Pattern.compile("\\[\\^[^\\]\\n]+\\]")),
            new Inline(LINK, Pattern.compile("(!?\\[[^\\]\\n]*\\])(\\([^)\\n]*\\))")),
            new Inline(MarkdownTokens.LINK_TEXT, Pattern.compile("\\[[^\\]\\n]+\\]\\[[^\\]\\n]*\\]")),
            new Inline(MarkdownTokens.LINK_URL, Pattern.compile("https?://[^\\s)>\\]]+")),
            new Inline(MarkdownTokens.BOLD, Pattern.compile(
                    "\\*\\*(?=\\S)(?:(?!\\*\\*).)+?(?<=\\S)\\*\\*|__(?=\\S)(?:(?!__).)+?(?<=\\S)__")),
            new Inline(MarkdownTokens.STRIKE, Pattern.compile("~~(?=\\S).+?(?<=\\S)~~")),
            new Inline(MarkdownTokens.ITALIC, Pattern.compile(
                    "\\*(?=[^\\s*]).+?(?<=[^\\s*])\\*|(?<!\\w)_(?=[^\\s_]).+?(?<=[^\\s_])_(?!\\w)")));

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern HEADING = Pattern.compile("^( {0,3})(#{1,6})([ \\t]+)(.*?)([ \\t]+#+[ \\t]*)?$");
    private static final Pattern HEADING_EMPTY = Pattern.compile("^ {0,3}#{1,6}[ \\t]*$");
    private static final Pattern RULE = Pattern.compile("^ {0,3}([-*_])([ \\t]*\\1){2,}[ \\t]*$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}(?:>[ \\t]?)+");
    private static final Pattern LIST = Pattern.compile("^([ \\t]*)([-*+]|\\d{1,9}[.)])([ \\t]+)");
    private static final Pattern TASK = Pattern.compile("^\\[[ xX]\\](?=[ \\t])");
    private static final Pattern SEPARATOR = Pattern.compile(
            "^[ \\t]*\\|?[ \\t]*:?-+:?[ \\t]*(\\|[ \\t]*:?-+:?[ \\t]*)+\\|?[ \\t]*$");

    private static final class State {
        char fence;
        int fenceLength;
        boolean frontMatter;
        boolean math;
        boolean comment;
    }

    @Override
    public List<HighlightSpan> highlight(CharSequence text) {
        String source = text.toString();
        List<HighlightSpan> spans = new ArrayList<>();
        State state = new State();
        int index = 0;
        int lineNumber = 0;
        while (index <= source.length()) {
            int newline = source.indexOf('\n', index);
            int next = newline < 0 ? source.length() : newline;
            int end = next > index && source.charAt(next - 1) == '\r' ? next - 1 : next;
            line(source.substring(index, end), index, lineNumber, state, spans);
            if (newline < 0) {
                break;
            }
            index = newline + 1;
            lineNumber++;
        }
        return spans;
    }

    private static void line(String line, int start, int lineNumber, State state, List<HighlightSpan> out) {
        int end = start + line.length();
        String stripped = line.strip();
        if (lineNumber == 0 && line.stripTrailing().equals("---")) {
            state.frontMatter = true;
            whole(start, end, MarkdownTokens.FRONT_MATTER, out);
            return;
        }
        if (state.frontMatter) {
            whole(start, end, MarkdownTokens.FRONT_MATTER, out);
            if (stripped.equals("---") || stripped.equals("...")) {
                state.frontMatter = false;
            }
            return;
        }
        if (state.fenceLength > 0) {
            if (closesFence(line, state)) {
                whole(start, end, MarkdownTokens.FENCE, out);
                state.fenceLength = 0;
            } else {
                whole(start, end, MarkdownTokens.CODE_BLOCK, out);
            }
            return;
        }
        Matcher fence = FENCE.matcher(line);
        if (fence.matches() && !(fence.group(1).charAt(0) == '`' && fence.group(2).indexOf('`') >= 0)) {
            state.fence = fence.group(1).charAt(0);
            state.fenceLength = fence.group(1).length();
            whole(start, end, MarkdownTokens.FENCE, out);
            return;
        }
        if (state.math) {
            whole(start, end, MarkdownTokens.MATH, out);
            if (line.contains("$$")) {
                state.math = false;
            }
            return;
        }
        if (stripped.startsWith("$$")) {
            whole(start, end, MarkdownTokens.MATH, out);
            state.math = !(stripped.length() > 2 && stripped.endsWith("$$"));
            return;
        }
        if (state.comment) {
            whole(start, end, MarkdownTokens.COMMENT, out);
            if (line.contains("-->")) {
                state.comment = false;
            }
            return;
        }
        if (stripped.startsWith("<!--") && !stripped.contains("-->")) {
            whole(start, end, MarkdownTokens.COMMENT, out);
            state.comment = true;
            return;
        }
        if (RULE.matcher(line).matches()) {
            whole(start, end, MarkdownTokens.HR, out);
            return;
        }
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            int marker = start + heading.end(1);
            out.add(new HighlightSpan(marker, start + heading.end(2), MarkdownTokens.HEADING_MARKER));
            if (heading.end(4) > heading.start(4)) {
                out.add(new HighlightSpan(start + heading.start(4), start + heading.end(4), MarkdownTokens.HEADING));
            }
            if (heading.group(5) != null) {
                out.add(new HighlightSpan(start + heading.start(5), start + heading.end(5),
                        MarkdownTokens.HEADING_MARKER));
            }
            return;
        }
        if (HEADING_EMPTY.matcher(line).matches()) {
            int marker = start + line.indexOf('#');
            out.add(new HighlightSpan(marker, marker + stripped.length(), MarkdownTokens.HEADING_MARKER));
            return;
        }
        if (line.indexOf('|') >= 0 && SEPARATOR.matcher(line).matches()) {
            whole(start, end, MarkdownTokens.TABLE, out);
            return;
        }
        int position = 0;
        Matcher quote = QUOTE.matcher(line);
        if (quote.find()) {
            out.add(new HighlightSpan(start, start + quote.group().stripTrailing().length(), MarkdownTokens.QUOTE));
            position = quote.end();
        }
        Matcher list = LIST.matcher(line);
        list.region(position, line.length());
        if (list.lookingAt()) {
            out.add(new HighlightSpan(start + list.start(2), start + list.end(2), MarkdownTokens.LIST));
            position = list.end();
            Matcher task = TASK.matcher(line);
            task.region(position, line.length());
            if (task.lookingAt()) {
                out.add(new HighlightSpan(start + position, start + task.end(), MarkdownTokens.TASK));
                position = task.end();
            }
        }
        if (line.stripLeading().startsWith("|")) {
            tableRow(line, position, start, out);
        } else {
            inline(line.substring(position), start + position, out);
        }
    }

    private static boolean closesFence(String line, State state) {
        int index = 0;
        while (index < line.length() && index < 3 && line.charAt(index) == ' ') {
            index++;
        }
        int run = 0;
        while (index + run < line.length() && line.charAt(index + run) == state.fence) {
            run++;
        }
        return run >= state.fenceLength && line.substring(index + run).isBlank();
    }

    private static void whole(int start, int end, String token, List<HighlightSpan> out) {
        if (end > start) {
            out.add(new HighlightSpan(start, end, token));
        }
    }

    /** Ячейки таблицы: разделители {@code |} отдельными токенами, содержимое ячеек — обычная строчная разметка. */
    private static void tableRow(String line, int from, int base, List<HighlightSpan> out) {
        int segmentStart = from;
        for (int index = from; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '\\') {
                index++;
            } else if (character == '|') {
                inline(line.substring(segmentStart, index), base + segmentStart, out);
                out.add(new HighlightSpan(base + index, base + index + 1, MarkdownTokens.TABLE));
                segmentStart = index + 1;
            }
        }
        if (segmentStart < line.length()) {
            inline(line.substring(segmentStart), base + segmentStart, out);
        }
    }

    private static void inline(String text, int base, List<HighlightSpan> out) {
        int position = 0;
        while (position < text.length()) {
            Inline best = null;
            Matcher bestMatcher = null;
            for (Inline candidate : INLINE) {
                Matcher matcher = candidate.pattern().matcher(text);
                if (matcher.find(position) && matcher.end() > matcher.start()
                        && (bestMatcher == null || matcher.start() < bestMatcher.start())) {
                    best = candidate;
                    bestMatcher = matcher;
                }
            }
            if (best == null) {
                return;
            }
            if (best.token() == ESCAPE) {
                position = bestMatcher.end();
                continue;
            }
            if (LINK.equals(best.token())) {
                out.add(new HighlightSpan(base + bestMatcher.start(1), base + bestMatcher.end(1),
                        MarkdownTokens.LINK_TEXT));
                out.add(new HighlightSpan(base + bestMatcher.start(2), base + bestMatcher.end(2),
                        MarkdownTokens.LINK_URL));
            } else {
                out.add(new HighlightSpan(base + bestMatcher.start(), base + bestMatcher.end(), best.token()));
            }
            position = bestMatcher.end();
        }
    }
}
