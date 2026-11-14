package ru.mcs.plugin.mdparser;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import java.util.ArrayList;
import java.util.List;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser;

/** Flexmark AST с инкрементальным dirty-window reparse и неизменяемыми результатами. */
final class FlexmarkMarkdownParser implements MarkdownParser {

    private final Parser parser = Parser.builder().extensions(ParserExtensions.all()).build();

    @Override
    public ParseResult parse(CharSequence text, long version) {
        String source = text.toString();
        ParsedWindow window = parseWindow(source, 0);
        return result(source, version, window.segments(), new ParseMetrics(window.segments().size(), 0, true));
    }

    @Override
    public ParseResult reparse(ParseResult previous, CharSequence text, long version, Change change) {
        if (!(previous instanceof FlexmarkParseResult old)) {
            return parse(text, version);
        }
        String source = text.toString();
        if (!validChange(old.text, source, change) || hasGlobalDefinitions(old)
                || introducesGlobalDefinition(source)) {
            return parse(source, version);
        }
        Dirty dirty = dirtyRange(old.segments, change);
        if (dirty == null) {
            return parse(source, version);
        }

        int first = dirty.first();
        int last = dirty.last();
        int delta = change.newLength() - change.oldLength();
        String oldFragment = old.text.substring(change.start(), change.start() + change.oldLength());
        String newFragment = source.substring(change.start(), change.start() + change.newLength());
        if (structuralBoundary(oldFragment, newFragment)) {
            if (first > 0) {
                first--;
            }
            if (last < old.segments.size() - 1) {
                last++;
            }
        } else if (first > 0 && change.start() == old.segments.get(first).start) {
            first--;
        }

        ParsedWindow replacement;
        while (true) {
            int newStart = Math.min(old.segments.get(first).start, change.start());
            int newEnd = Math.max(old.segments.get(last).end + delta,
                    change.start() + change.newLength());
            replacement = parseWindow(source.substring(newStart, newEnd), newStart);
            if (last >= old.segments.size() - 1) {
                break;
            }
            if (hasUnclosedTail(replacement)
                    || continuesParagraph(replacement, source, old.segments.get(last + 1).start + delta)) {
                last++;
            } else {
                break;
            }
        }

        List<Segment> merged = new ArrayList<>(old.segments.size() + replacement.segments().size());
        merged.addAll(old.segments.subList(0, first));
        merged.addAll(replacement.segments());
        for (int i = last + 1; i < old.segments.size(); i++) {
            merged.add(old.segments.get(i).shift(delta));
        }
        updateSegmentEnds(merged, source.length());
        int reused = first + old.segments.size() - last - 1;
        return result(source, version, merged,
                new ParseMetrics(replacement.segments().size(), reused, false));
    }

    private ParsedWindow parseWindow(String source, int baseOffset) {
        Document document = parser.parse(source);
        List<Segment> segments = new ArrayList<>();
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            segments.add(new Segment(NodeSpanMapper.tree(child, source, baseOffset, 1)));
        }
        segments = ObsidianSpanProcessor.apply(source, baseOffset, segments);
        updateSegmentEnds(segments, baseOffset + source.length());
        return new ParsedWindow(segments);
    }

    private static FlexmarkParseResult result(String text, long version, List<Segment> segments,
                                               ParseMetrics metrics) {
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, text.length(), 0));
        for (Segment segment : segments) {
            spans.addAll(segment.spans);
        }
        List<Segment> immutableSegments = segments.stream().map(Segment::immutableCopy).toList();
        return new FlexmarkParseResult(text, version, List.copyOf(spans), immutableSegments, metrics);
    }

    private static Dirty dirtyRange(List<Segment> segments, Change change) {
        int first = -1;
        int last = -1;
        int oldEnd = change.start() + change.oldLength();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            boolean touched = change.oldLength() == 0
                    ? change.start() >= segment.start && change.start() <= segment.end
                    : change.start() < segment.end && oldEnd > segment.start;
            if (touched) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        return first < 0 ? null : new Dirty(first, last);
    }

    private static boolean structuralBoundary(String oldFragment, String newFragment) {
        return containsAny(oldFragment, "\n`~|$") || containsAny(newFragment, "\n`~|$");
    }

    private static boolean containsAny(String text, String characters) {
        for (int i = 0; i < characters.length(); i++) {
            if (text.indexOf(characters.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnclosedTail(ParsedWindow window) {
        if (window.segments().isEmpty()) {
            return false;
        }
        BlockSpan last = window.segments().getLast().spans.getFirst();
        return last.kind() == BlockKind.FENCED_CODE
                && "false".equals(last.attributes().get("closed"));
    }

    private static boolean continuesParagraph(ParsedWindow window, String source, int nextStart) {
        if (window.segments().isEmpty()) {
            return false;
        }
        BlockSpan tail = window.segments().getLast().spans.getFirst();
        if (tail.kind() != BlockKind.PARAGRAPH || tail.end() > nextStart) {
            return false;
        }
        int breaks = 0;
        for (int i = nextStart - 1; i >= tail.start(); i--) {
            char c = source.charAt(i);
            if (c == '\n') {
                breaks++;
                if (breaks == 2) {
                    return false;
                }
            } else if (c != '\r' && c != ' ' && c != '\t') {
                break;
            }
        }
        return true;
    }

    private static boolean hasGlobalDefinitions(FlexmarkParseResult result) {
        return result.spans.stream().anyMatch(span -> span.kind() == BlockKind.REFERENCE_DEFINITION
                || span.kind() == BlockKind.FOOTNOTE_DEFINITION);
    }

    private static boolean introducesGlobalDefinition(String newText) {
        return newText.contains("[^") || newText.contains("]:");
    }

    private static boolean validChange(String oldText, String newText, Change change) {
        if (change.start() + change.oldLength() > oldText.length()
                || change.start() + change.newLength() > newText.length()
                || newText.length() != oldText.length() - change.oldLength() + change.newLength()) {
            return false;
        }
        return oldText.regionMatches(0, newText, 0, change.start())
                && oldText.regionMatches(change.start() + change.oldLength(), newText,
                change.start() + change.newLength(),
                oldText.length() - change.start() - change.oldLength());
    }

    private static void updateSegmentEnds(List<Segment> segments, int documentEnd) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            segment.end = i + 1 < segments.size() ? segments.get(i + 1).start : documentEnd;
        }
    }

    static final class Segment {
        final int start;
        List<BlockSpan> spans;
        int end;

        Segment(List<BlockSpan> spans) {
            if (spans.isEmpty()) {
                throw new IllegalArgumentException("A segment needs its top-level span");
            }
            this.spans = new ArrayList<>(spans);
            this.start = spans.getFirst().start();
        }

        Segment shift(int delta) {
            List<BlockSpan> shifted = spans.stream()
                    .map(span -> new BlockSpan(span.kind(), span.start() + delta, span.end() + delta,
                            span.depth(), span.attributes()))
                    .toList();
            return new Segment(shifted);
        }

        Segment immutableCopy() {
            Segment copy = new Segment(List.copyOf(spans));
            copy.end = end;
            return copy;
        }
    }

    private record ParsedWindow(List<Segment> segments) {
    }

    private record Dirty(int first, int last) {
    }

    private static final class FlexmarkParseResult implements ParseResult {
        private final String text;
        private final long version;
        private final List<BlockSpan> spans;
        private final List<Segment> segments;
        private final ParseMetrics metrics;

        private FlexmarkParseResult(String text, long version, List<BlockSpan> spans,
                                    List<Segment> segments, ParseMetrics metrics) {
            this.text = text;
            this.version = version;
            this.spans = spans;
            this.segments = segments;
            this.metrics = metrics;
        }

        @Override
        public long version() {
            return version;
        }

        @Override
        public List<BlockSpan> spans() {
            return spans;
        }

        @Override
        public ParseMetrics metrics() {
            return metrics;
        }
    }
}
