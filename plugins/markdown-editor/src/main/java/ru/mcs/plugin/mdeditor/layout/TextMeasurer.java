package ru.mcs.plugin.mdeditor.layout;

import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.List;

/** Word-wrap по styled runs; все измерения используют явный FontRenderContext. */
final class TextMeasurer {

    static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);

    private final int width;
    private final InlineContent.Style defaultStyle;
    private final List<LineBox> lines = new ArrayList<>();
    private List<InlineRun> currentRuns = new ArrayList<>();
    private float currentWidth;
    private int currentSourceOffset;
    private boolean endedWithNewline;

    TextMeasurer(int width, InlineContent.Style defaultStyle) {
        this.width = Math.max(1, width);
        this.defaultStyle = defaultStyle;
    }

    List<LineBox> layout(InlineContent content, int x, int y) {
        return layout(content, x, y, false);
    }

    List<LineBox> layout(InlineContent content, int x, int y, boolean preserveTrailingEmptyLine) {
        currentSourceOffset = content.sourceStart(0);
        for (InlineContent.Segment segment : content.segments()) {
            int tokenStart = 0;
            int index = 0;
            while (index < segment.text().length()) {
                char ch = segment.text().charAt(index);
                if (ch == '\n') {
                    if (tokenStart < index) {
                        addToken(segment.slice(tokenStart, index));
                    }
                    // Пустая строка относится к offset перед завершающим её '\n'.
                    // Offset после перевода строки становится началом следующей physical line.
                    finishLine();
                    currentSourceOffset = segment.sourceOffsets()[index + 1];
                    endedWithNewline = true;
                    index++;
                    tokenStart = index;
                } else {
                    endedWithNewline = false;
                    boolean whitespace = Character.isWhitespace(ch);
                    int next = index + 1;
                    while (next < segment.text().length() && segment.text().charAt(next) != '\n'
                            && Character.isWhitespace(segment.text().charAt(next)) == whitespace) {
                        next++;
                    }
                    if (tokenStart < index) {
                        addToken(segment.slice(tokenStart, index));
                    }
                    addToken(segment.slice(index, next));
                    index = next;
                    tokenStart = index;
                }
            }
            if (tokenStart < segment.text().length()) {
                addToken(segment.slice(tokenStart, segment.text().length()));
            }
            currentSourceOffset = segment.sourceOffsets()[segment.text().length()];
        }
        if (!currentRuns.isEmpty() || lines.isEmpty() || preserveTrailingEmptyLine && endedWithNewline) {
            finishLine();
        }
        int lineY = y;
        for (LineBox line : lines) {
            int ascent = 0;
            int height = 0;
            for (InlineRun run : line.runs()) {
                LineMetrics metrics = run.font().getLineMetrics(run.text(), RENDER_CONTEXT);
                ascent = Math.max(ascent, Math.round(metrics.getAscent()));
                height = Math.max(height, Math.round(metrics.getHeight()));
                height = Math.max(height, (int) Math.round(run.style().lineHeightPx()));
            }
            if (line.runs().isEmpty()) {
                LineMetrics metrics = defaultStyle.font().getLineMetrics("Ag", RENDER_CONTEXT);
                ascent = Math.round(metrics.getAscent());
                height = Math.max(Math.round(metrics.getHeight()),
                        (int) Math.round(defaultStyle.computed().lineHeightPx()));
            }
            line.place(x, lineY, Math.max(1, height), Math.max(1, ascent));
            lineY += line.height();
        }
        return List.copyOf(lines);
    }

    private void addToken(InlineContent.Segment token) {
        TextLayout layout = new TextLayout(token.text(), token.style().font(), RENDER_CONTEXT);
        if (currentWidth + layout.getAdvance() <= width || currentRuns.isEmpty()
                && layout.getAdvance() <= width) {
            addRun(token, layout);
            return;
        }
        if (!currentRuns.isEmpty()) {
            finishLine();
        }
        if (layout.getAdvance() <= width) {
            addRun(token, layout);
            return;
        }
        int start = 0;
        while (start < token.text().length()) {
            int end = fit(token, start);
            InlineContent.Segment part = token.slice(start, end);
            addRun(part, new TextLayout(part.text(), part.style().font(), RENDER_CONTEXT));
            start = end;
            if (start < token.text().length()) {
                finishLine();
            }
        }
    }

    private int fit(InlineContent.Segment token, int start) {
        int low = start + 1;
        int high = token.text().length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String value = token.text().substring(start, middle);
            if (new TextLayout(value, token.style().font(), RENDER_CONTEXT).getAdvance() <= width) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        int result = Math.max(start + 1, low);
        if (result > start + 1 && Character.isHighSurrogate(token.text().charAt(result - 1))) {
            result--;
        }
        return result;
    }

    private void addRun(InlineContent.Segment token, TextLayout layout) {
        currentRuns.add(new InlineRun(token.text(), token.sourceOffsets(), layout, token.style().font(),
                token.style().computed(), token.style().linkTarget(), token.style().taskOffset(),
                token.style().taskChecked()));
        currentWidth += layout.getAdvance();
        currentSourceOffset = token.sourceOffsets()[token.sourceOffsets().length - 1];
    }

    private void finishLine() {
        lines.add(new LineBox(currentRuns, currentSourceOffset));
        currentRuns = new ArrayList<>();
        currentWidth = 0;
    }
}
