package ru.mcs.plugin.mdeditor.view;

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.mcs.plugin.mdeditor.model.DocumentChange;
import ru.mcs.plugin.mdeditor.model.DocumentModel;

/** Построчная word-wrap раскладка. После правки заново измеряются только затронутые физические строки. */
final class TextLayoutEngine {

    static final int HORIZONTAL_PADDING = 12;
    static final int VERTICAL_PADDING = 8;

    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);

    private DocumentModel document;
    private DocumentLineIndex lines;
    private Font font;
    private int width = 800;
    private int lineHeight = 18;
    private int ascent = 14;
    private final List<LogicalLine> logicalLines = new ArrayList<>();

    void setDocument(DocumentModel document) {
        this.document = document;
        lines = new DocumentLineIndex(document);
        rebuildAll();
    }

    void clear() {
        document = null;
        lines = null;
        logicalLines.clear();
    }

    void setFont(Font font) {
        if (font == null || font.equals(this.font)) {
            return;
        }
        this.font = font;
        lineHeight = Math.max(1, Math.round(font.getLineMetrics("Ag", RENDER_CONTEXT).getHeight()) + 2);
        ascent = Math.round(font.getLineMetrics("Ag", RENDER_CONTEXT).getAscent());
        rebuildAll();
    }

    void setWidth(int width) {
        int available = Math.max(1, width - HORIZONTAL_PADDING * 2);
        if (available == this.width) {
            return;
        }
        this.width = available;
        rebuildAll();
    }

    void reload() {
        if (document != null) {
            lines = new DocumentLineIndex(document);
            rebuildAll();
        }
    }

    void changed(DocumentChange change) {
        if (document == null) {
            return;
        }
        DocumentLineIndex.ChangedLines changed = lines.update(document, change);
        List<LogicalLine> replacement = new ArrayList<>();
        for (int line = changed.firstLine(); line <= changed.newLastLine(); line++) {
            replacement.add(layoutLogicalLine(line));
        }
        logicalLines.subList(changed.firstLine(), changed.oldLastLine() + 1).clear();
        logicalLines.addAll(changed.firstLine(), replacement);
        updateVerticalPositions();
    }

    int lineHeight() {
        return lineHeight;
    }

    int preferredHeight() {
        if (logicalLines.isEmpty()) {
            return VERTICAL_PADDING * 2 + lineHeight;
        }
        LogicalLine last = logicalLines.getLast();
        return last.y + last.height + VERTICAL_PADDING * 2;
    }

    int logicalLineAt(int offset) {
        return lines.lineAt(offset);
    }

    int logicalLineStart(int line) {
        return lines.lineStart(line);
    }

    int logicalLineEnd(int line) {
        return lines.lineEnd(line, document.length());
    }

    int nextLogicalLineStart(int line) {
        return lines.nextLineStart(line, document.length());
    }

    Rectangle caretRect(int offset) {
        checkDocument();
        if (logicalLines.isEmpty()) {
            return new Rectangle(HORIZONTAL_PADDING, VERTICAL_PADDING, 1, lineHeight);
        }
        int safeOffset = Math.max(0, Math.min(offset, document.length()));
        VisualLine visual = visualAt(safeOffset);
        int x = HORIZONTAL_PADDING + Math.round(caretX(visual, safeOffset));
        return new Rectangle(x, visual.y, 1, lineHeight);
    }

    int hitTest(Point point) {
        checkDocument();
        if (logicalLines.isEmpty()) {
            return 0;
        }
        if (point.y < VERTICAL_PADDING) {
            return 0;
        }
        LogicalLine logical = logicalAtY(point.y);
        if (point.y >= logical.y + logical.height) {
            return document.length();
        }
        int visualIndex = Math.max(0, Math.min(logical.visuals.size() - 1,
                (point.y - logical.y) / lineHeight));
        VisualLine visual = logical.visuals.get(visualIndex);
        if (visual.text == null) {
            return visual.start;
        }
        TextHitInfo hit = visual.text.hitTestChar(point.x - HORIZONTAL_PADDING,
                Math.max(0, point.y - visual.y));
        return visual.start + hit.getInsertionIndex();
    }

    int homeOffset(int offset) {
        return visualAt(offset).start;
    }

    int endOffset(int offset) {
        return visualAt(offset).end;
    }

    List<VisualLine> visibleLines(Rectangle clip) {
        if (document == null || logicalLines.isEmpty()) {
            return List.of();
        }
        int minY = Math.max(VERTICAL_PADDING, clip.y);
        int maxY = clip.y + clip.height;
        List<VisualLine> result = new ArrayList<>();
        int line = Math.max(0, logicalIndexAtY(minY));
        for (; line < logicalLines.size(); line++) {
            LogicalLine logical = logicalLines.get(line);
            if (logical.y >= maxY) {
                break;
            }
            if (logical.y + logical.height < minY) {
                continue;
            }
            for (VisualLine visual : logical.visuals) {
                if (visual.y + lineHeight >= minY && visual.y <= maxY) {
                    result.add(visual);
                }
            }
        }
        return result;
    }

    float caretX(VisualLine visual, int offset) {
        if (visual.text == null) {
            return 0;
        }
        int relative = Math.max(0, Math.min(offset - visual.start, visual.end - visual.start));
        int length = visual.end - visual.start;
        TextHitInfo hit = relative == length && length > 0
                ? TextHitInfo.trailing(length - 1)
                : TextHitInfo.leading(relative);
        return visual.text.getCaretInfo(hit)[0];
    }

    int ascent() {
        return ascent;
    }

    private VisualLine visualAt(int offset) {
        int logicalIndex = lines.lineAt(offset);
        LogicalLine logical = logicalLines.get(logicalIndex);
        for (int i = logical.visuals.size() - 1; i >= 0; i--) {
            VisualLine visual = logical.visuals.get(i);
            if (offset >= visual.start) {
                return visual;
            }
        }
        return logical.visuals.getFirst();
    }

    private LogicalLine logicalAtY(int y) {
        return logicalLines.get(logicalIndexAtY(y));
    }

    private int logicalIndexAtY(int y) {
        int low = 0;
        int high = logicalLines.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            LogicalLine line = logicalLines.get(middle);
            if (y < line.y) {
                high = middle - 1;
            } else if (y >= line.y + line.height) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return Math.max(0, Math.min(logicalLines.size() - 1, high));
    }

    private void rebuildAll() {
        logicalLines.clear();
        if (document == null || font == null) {
            return;
        }
        for (int line = 0; line < lines.lineCount(); line++) {
            logicalLines.add(layoutLogicalLine(line));
        }
        updateVerticalPositions();
    }

    private LogicalLine layoutLogicalLine(int line) {
        int start = lines.lineStart(line);
        int end = lines.lineEnd(line, document.length());
        List<VisualLine> visuals = new ArrayList<>();
        if (start == end) {
            visuals.add(new VisualLine(start, end, null));
        } else {
            BreakIterator breaks = BreakIterator.getLineInstance(Locale.ROOT);
            String logicalText = document.text().subSequence(start, end).toString();
            breaks.setText(logicalText);
            int visualStart = start;
            while (visualStart < end) {
                int fit = fitOffset(visualStart, end);
                int visualEnd = fit;
                if (fit < end) {
                    int boundary = breaks.preceding(fit - start + 1);
                    if (boundary > visualStart - start) {
                        visualEnd = start + boundary;
                    }
                }
                if (visualEnd <= visualStart) {
                    visualEnd = Math.min(end, visualStart + Character.charCount(
                            Character.codePointAt(document.text(), visualStart)));
                }
                visuals.add(new VisualLine(visualStart, visualEnd, textLayout(visualStart, visualEnd)));
                visualStart = visualEnd;
            }
        }
        return new LogicalLine(visuals);
    }

    private int fitOffset(int start, int end) {
        String remaining = document.text().subSequence(start, end).toString();
        TextLayout full = new TextLayout(remaining, font, RENDER_CONTEXT);
        if (full.getAdvance() <= width) {
            return end;
        }
        int low = start + 1;
        int high = end;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (textLayout(start, middle).getAdvance() <= width) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return Math.max(start + 1, low - 1);
    }

    private TextLayout textLayout(int start, int end) {
        return new TextLayout(document.text().subSequence(start, end).toString(), font, RENDER_CONTEXT);
    }

    private void updateVerticalPositions() {
        int y = VERTICAL_PADDING;
        for (LogicalLine logical : logicalLines) {
            logical.y = y;
            logical.height = logical.visuals.size() * lineHeight;
            for (VisualLine visual : logical.visuals) {
                visual.y = y;
                y += lineHeight;
            }
        }
    }

    private void checkDocument() {
        if (document == null) {
            throw new IllegalStateException("No document is attached to the editor");
        }
    }

    static final class VisualLine {
        private final int start;
        private final int end;
        private final TextLayout text;
        private int y;

        private VisualLine(int start, int end, TextLayout text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }

        int start() {
            return start;
        }

        int end() {
            return end;
        }

        int y() {
            return y;
        }

        TextLayout text() {
            return text;
        }
    }

    private static final class LogicalLine {
        private final List<VisualLine> visuals;
        private int y;
        private int height;

        private LogicalLine(List<VisualLine> visuals) {
            this.visuals = visuals;
        }
    }
}
