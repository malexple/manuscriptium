package ru.mcs.plugin.mdparser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.mcs.plugin.mdparser.FlexmarkMarkdownParser.Segment;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;

/** Source-level расширения, для которых у Flexmark 0.64.8 нет готового AST-узла. */
final class ObsidianSpanProcessor {

    private ObsidianSpanProcessor() {
    }

    static List<Segment> apply(String source, int baseOffset, List<Segment> segments) {
        List<Segment> result = new ArrayList<>();
        for (Segment segment : segments) {
            List<BlockSpan> spans = segment.spans.stream()
                    .map(span -> callout(span, source, baseOffset))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            result.add(new Segment(spans));
        }
        replaceMathBlocks(source, baseOffset, result);
        addInlineMath(source, baseOffset, result);
        return result;
    }

    private static BlockSpan callout(BlockSpan span, String source, int baseOffset) {
        if (span.kind() != BlockKind.BLOCK_QUOTE) {
            return span;
        }
        int local = span.start() - baseOffset;
        int lineEnd = lineEnd(source, local);
        int p = skipIndent(source, local, lineEnd);
        if (p >= lineEnd || source.charAt(p) != '>') {
            return span;
        }
        p++;
        while (p < lineEnd && (source.charAt(p) == ' ' || source.charAt(p) == '\t')) {
            p++;
        }
        if (p + 3 >= lineEnd || source.charAt(p) != '[' || source.charAt(p + 1) != '!') {
            return span;
        }
        int end = source.indexOf(']', p + 2);
        if (end < 0 || end > lineEnd || end == p + 2) {
            return span;
        }
        String type = source.substring(p + 2, end).trim();
        if (type.isEmpty() || !type.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '-')) {
            return span;
        }
        java.util.Map<String, String> attributes = new java.util.HashMap<>(span.attributes());
        attributes.put("type", type.toLowerCase(Locale.ROOT));
        if (end + 1 < lineEnd && (source.charAt(end + 1) == '+' || source.charAt(end + 1) == '-')) {
            attributes.put("fold", Character.toString(source.charAt(end + 1)));
        }
        return new BlockSpan(BlockKind.CALLOUT, span.start(), span.end(), span.depth(), attributes);
    }

    private static void replaceMathBlocks(String source, int baseOffset, List<Segment> segments) {
        int lineStart = 0;
        while (lineStart < source.length()) {
            int lineEnd = lineEnd(source, lineStart);
            int content = skipIndent(source, lineStart, lineEnd);
            if (!isMathDelimiter(source, content, lineEnd)) {
                lineStart = nextLine(source, lineEnd);
                continue;
            }
            int closing = findMathClosing(source, nextLine(source, lineEnd));
            if (closing < 0) {
                lineStart = nextLine(source, lineEnd);
                continue;
            }
            int absoluteStart = baseOffset + content;
            int absoluteEnd = baseOffset + lineEnd(source, closing);
            int first = segmentAtStart(segments, absoluteStart);
            if (first >= 0) {
                int after = first + 1;
                while (after < segments.size() && segments.get(after).start < absoluteEnd) {
                    after++;
                }
                BlockSpan math = new BlockSpan(BlockKind.MATH_BLOCK, absoluteStart, absoluteEnd, 1,
                        Map.of("display", "block"));
                segments.subList(first, after).clear();
                segments.add(first, new Segment(List.of(math)));
            }
            lineStart = nextLine(source, lineEnd(source, closing));
        }
    }

    private static void addInlineMath(String source, int baseOffset, List<Segment> segments) {
        for (int open = 0; open < source.length(); open++) {
            if (source.charAt(open) != '$' || escaped(source, open)
                    || (open + 1 < source.length() && source.charAt(open + 1) == '$')
                    || (open > 0 && source.charAt(open - 1) == '$')
                    || excluded(segments, baseOffset + open)) {
                continue;
            }
            if (open + 1 >= source.length() || Character.isWhitespace(source.charAt(open + 1))) {
                continue;
            }
            int close = open + 1;
            while (close < source.length() && source.charAt(close) != '\n' && source.charAt(close) != '\r') {
                if (source.charAt(close) == '$' && !escaped(source, close)
                        && !Character.isWhitespace(source.charAt(close - 1))) {
                    break;
                }
                close++;
            }
            if (close >= source.length() || close == open + 1 || source.charAt(close) != '$'
                    || excluded(segments, baseOffset + close)) {
                continue;
            }
            int absoluteStart = baseOffset + open;
            int absoluteEnd = baseOffset + close + 1;
            for (Segment segment : segments) {
                BlockSpan parent = segment.spans.stream()
                        .filter(span -> span.kind() != BlockKind.TEXT
                                && span.start() <= absoluteStart && span.end() >= absoluteEnd)
                        .max(Comparator.comparingInt(BlockSpan::depth))
                        .orElse(null);
                if (parent != null) {
                    segment.spans.add(new BlockSpan(BlockKind.MATH_INLINE, absoluteStart, absoluteEnd,
                            parent.depth() + 1));
                    segment.spans.sort(Comparator.comparingInt(BlockSpan::start)
                            .thenComparingInt(BlockSpan::depth)
                            .thenComparingInt(BlockSpan::end));
                    break;
                }
            }
            open = close;
        }
    }

    private static boolean excluded(List<Segment> segments, int offset) {
        for (Segment segment : segments) {
            for (BlockSpan span : segment.spans) {
                if (span.start() <= offset && offset < span.end()
                        && (span.kind() == BlockKind.CODE || span.kind() == BlockKind.FENCED_CODE
                        || span.kind() == BlockKind.INDENTED_CODE || span.kind() == BlockKind.FRONT_MATTER
                        || span.kind() == BlockKind.MATH_BLOCK)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int segmentAtStart(List<Segment> segments, int start) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).start == start) {
                return i;
            }
        }
        return -1;
    }

    private static int findMathClosing(String source, int lineStart) {
        while (lineStart < source.length()) {
            int lineEnd = lineEnd(source, lineStart);
            if (isMathDelimiter(source, skipIndent(source, lineStart, lineEnd), lineEnd)) {
                return lineStart;
            }
            lineStart = nextLine(source, lineEnd);
        }
        return -1;
    }

    private static boolean isMathDelimiter(String source, int start, int end) {
        if (start + 2 > end || !source.startsWith("$$", start)) {
            return false;
        }
        for (int i = start + 2; i < end; i++) {
            if (source.charAt(i) != ' ' && source.charAt(i) != '\t') {
                return false;
            }
        }
        return true;
    }

    private static int skipIndent(String source, int start, int end) {
        int p = start;
        int spaces = 0;
        while (p < end && spaces < 3 && source.charAt(p) == ' ') {
            p++;
            spaces++;
        }
        return p;
    }

    private static int lineEnd(String source, int start) {
        int end = Math.max(0, start);
        while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
            end++;
        }
        return end;
    }

    private static int nextLine(String source, int lineEnd) {
        if (lineEnd >= source.length()) {
            return source.length();
        }
        if (source.charAt(lineEnd) == '\r' && lineEnd + 1 < source.length()
                && source.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
    }

    private static boolean escaped(String source, int offset) {
        int slashes = 0;
        for (int i = offset - 1; i >= 0 && source.charAt(i) == '\\'; i--) {
            slashes++;
        }
        return (slashes & 1) != 0;
    }
}
