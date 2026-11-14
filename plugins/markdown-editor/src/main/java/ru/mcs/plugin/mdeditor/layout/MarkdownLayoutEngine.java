package ru.mcs.plugin.mdeditor.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import ru.mcs.plugin.mdeditor.mode.FocusMode;
import ru.mcs.plugin.mdeditor.model.DocumentModel;
import ru.mcs.plugin.mdeditor.model.Range;
import ru.mcs.plugin.mdeditor.render.BlockRenderer;
import ru.mcs.plugin.mdeditor.render.DrawSink;
import ru.mcs.plugin.mdeditor.render.Graphics2DDrawSink;
import ru.mcs.plugin.mdeditor.render.SourceBlockRenderer;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.CssBorder;
import ru.mcs.spi.editor.style.CssEdges;
import ru.mcs.spi.editor.style.CssLength;
import ru.mcs.spi.editor.style.EditorStyleService;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleElement;

/** AST → BlockBox/LineBox/InlineRun. Source Mode остаётся на TextLayoutEngine. */
public final class MarkdownLayoutEngine {

    private static final int DEFAULT_WIDTH = 800;
    private static final List<StyleElement> WRITE_ANCESTORS =
            List.of(new StyleElement("div", "write", List.of(), 0));

    private final LayoutCache cache = new LayoutCache();
    private final BlockRenderer blockRenderer = new BlockRenderer();
    private final SourceBlockRenderer sourceRenderer = new SourceBlockRenderer();
    private final FocusMode focusMode = new FocusMode();

    private DocumentModel document;
    private AstIndex ast;
    private EditorStyleService styles;
    private int width = DEFAULT_WIDTH;
    private int caretOffset;
    private LayoutResult current;

    public void setContext(DocumentModel document, AstIndex ast, EditorStyleService styles) {
        this.document = document;
        this.ast = ast;
        this.styles = styles;
        cache.clear();
        current = null;
    }

    public void setAst(AstIndex ast) {
        this.ast = ast;
        cache.clear();
        current = null;
    }

    public void setWidth(int width) {
        int safe = Math.max(1, width);
        if (this.width != safe) {
            this.width = safe;
            cache.clear();
            current = null;
        }
    }

    public void setCaretOffset(int caretOffset) {
        this.caretOffset = caretOffset;
    }

    public void setFocusMode(boolean enabled) {
        focusMode.setEnabled(enabled);
    }

    public void clear() {
        document = null;
        ast = null;
        cache.clear();
        current = null;
    }

    public LayoutResult layout() {
        if (document == null || styles == null) {
            return new LayoutResult(0, width, 0, width, 0, List.of());
        }
        List<BlockSpan> spans = validAst() ? ast.topLevelBlocks() : List.of();
        if (spans.isEmpty()) {
            spans = List.of(new BlockSpan(BlockKind.PARAGRAPH, 0, document.length(), 1));
        }
        int activeStart = activeBlockStart(spans);
        LayoutResult cached = cache.get(document.version(), width, activeStart);
        if (cached != null) {
            current = cached;
            return cached;
        }
        current = rebuild(spans, activeStart);
        cache.put(current, activeStart);
        return current;
    }

    public Color background() {
        if (styles == null) {
            return Color.WHITE;
        }
        Color color = rootStyle().backgroundColor();
        return color.getAlpha() == 0 ? Color.WHITE : color;
    }

    public Color foreground() {
        return styles == null ? Color.DARK_GRAY : rootStyle().color();
    }

    public void paint(Graphics2D graphics, Rectangle clip, Range selection) {
        LayoutResult result = layout();
        List<BlockBox> visible = result.blocks().stream()
                .filter(block -> block.y() <= clip.y + clip.height && block.y() + block.height() >= clip.y)
                .toList();
        ComputedStyle root = rootStyle();
        for (BlockBox block : visible) {
            if (!block.source()) {
                Graphics2D target = dimmed(graphics, block, root);
                try {
                    blockRenderer.paintBackground(new Graphics2DDrawSink(target), block);
                } finally {
                    if (target != graphics) {
                        target.dispose();
                    }
                }
            }
        }
        if (selection != null && selection.length() > 0) {
            graphics.setColor(selectionColor());
            for (Rectangle rectangle : selectionRects(selection, clip)) {
                graphics.fill(rectangle);
            }
        }
        for (BlockBox block : visible) {
            Graphics2D target = dimmed(graphics, block, root);
            try {
                DrawSink sink = new Graphics2DDrawSink(target);
                if (block.source()) {
                    sourceRenderer.paint(sink, block);
                } else {
                    blockRenderer.paintContent(sink, block);
                }
            } finally {
                if (target != graphics) {
                    target.dispose();
                }
            }
        }
    }

    public int hitTest(Point point) {
        LayoutResult result = layout();
        if (result.blocks().isEmpty()) {
            return 0;
        }
        BlockBox block = result.blockAtY(point.y);
        if (block == null) {
            return 0;
        }
        if (block.lines().isEmpty()) {
            return block.span().start();
        }
        LineBox line = lineAtY(block, point.y);
        if (line.runs().isEmpty()) {
            return line.sourceStart();
        }
        if (point.x < line.runs().getFirst().x()) {
            return line.runs().getFirst().sourceStart();
        }
        for (InlineRun run : line.runs()) {
            if (point.x <= run.x() + run.advance()) {
                return run.hitOffset(point.x - run.x(), Math.max(0, point.y - line.y()));
            }
        }
        return line.runs().getLast().sourceEnd();
    }

    public Rectangle caretRect(int offset) {
        LayoutResult result = layout();
        if (result.blocks().isEmpty()) {
            return new Rectangle(12, 8, 1, 20);
        }
        BlockBox block = blockAtOffset(result, offset);
        if (block.lines().isEmpty()) {
            return new Rectangle(block.contentX(), block.y(), 1, 20);
        }
        LineBox line = lineAtOffset(block, offset);
        if (line.runs().isEmpty()) {
            return new Rectangle(line.x(), line.y(), 1, line.height());
        }
        for (InlineRun run : line.runs()) {
            if (offset >= run.sourceStart() && offset <= run.sourceEnd()) {
                return new Rectangle(Math.round(run.caretX(offset)), line.y(), 1, line.height());
            }
            if (offset < run.sourceStart()) {
                return new Rectangle(run.x(), line.y(), 1, line.height());
            }
        }
        InlineRun last = line.runs().getLast();
        return new Rectangle(Math.round(last.caretX(last.sourceEnd())), line.y(), 1, line.height());
    }

    public int homeOffset(int offset) {
        LineBox line = lineAtOffset(layout().blocks().isEmpty() ? null : blockAtOffset(layout(), offset), offset);
        return line == null || line.runs().isEmpty() ? offset : line.runs().getFirst().sourceStart();
    }

    public int endOffset(int offset) {
        LineBox line = lineAtOffset(layout().blocks().isEmpty() ? null : blockAtOffset(layout(), offset), offset);
        return line == null || line.runs().isEmpty() ? offset : line.runs().getLast().sourceEnd();
    }

    public int lineHeightAt(int offset) {
        LayoutResult result = layout();
        if (result.blocks().isEmpty()) {
            return 20;
        }
        LineBox line = lineAtOffset(blockAtOffset(result, offset), offset);
        return line == null ? 20 : line.height();
    }

    public int taskCheckboxAt(Point point) {
        for (BlockBox block : layout().blocks()) {
            for (LineBox line : block.lines()) {
                for (InlineRun run : line.runs()) {
                    if (run.taskOffset() >= 0) {
                        Rectangle bounds = run.bounds(line.y(), line.height());
                        bounds.grow(2, 1);
                        if (bounds.contains(point)) {
                            return run.taskOffset();
                        }
                    }
                }
            }
        }
        return -1;
    }

    public String linkAt(Point point) {
        for (BlockBox block : layout().blocks()) {
            for (LineBox line : block.lines()) {
                for (InlineRun run : line.runs()) {
                    if (run.linkTarget() != null && run.bounds(line.y(), line.height()).contains(point)) {
                        return run.linkTarget();
                    }
                }
            }
        }
        return null;
    }

    public List<Rectangle> selectionRects(Range selection, Rectangle clip) {
        List<Rectangle> rectangles = new ArrayList<>();
        for (BlockBox block : layout().blocks()) {
            for (LineBox line : block.lines()) {
                if (line.y() > clip.y + clip.height || line.y() + line.height() < clip.y
                        || selection.end() < line.sourceStart() || selection.start() > line.sourceEnd()) {
                    continue;
                }
                int start = Math.max(selection.start(), line.sourceStart());
                int end = Math.min(selection.end(), line.sourceEnd());
                int x1 = xInLine(line, start);
                int x2 = xInLine(line, end);
                if (selection.end() > line.sourceEnd()) {
                    x2 = line.endX() + 3;
                }
                rectangles.add(new Rectangle(Math.min(x1, x2), line.y(),
                        Math.max(1, Math.abs(x2 - x1)), line.height()));
            }
        }
        return rectangles;
    }

    public long layoutCount() {
        return cache.layoutCount();
    }

    private LayoutResult rebuild(List<BlockSpan> spans, int activeStart) {
        String raw = document.text().toString();
        ComputedStyle root = rootStyle();
        int viewport = Math.max(1, width);
        int rootHorizontal = horizontal(root.padding(), root.fontSizePx()) + horizontal(root.margin(), root.fontSizePx());
        int requestedWidth = root.width() == null ? viewport - rootHorizontal
                : px(root.width(), viewport, root.fontSizePx());
        int maxWidth = root.maxWidth() == null ? requestedWidth
                : px(root.maxWidth(), viewport, root.fontSizePx());
        int writeWidth = Math.max(1, Math.min(requestedWidth, maxWidth));
        int writeX = Math.max(0, (viewport - writeWidth) / 2);
        int contentX = writeX + px(root.padding().left(), root.fontSizePx(), root.fontSizePx())
                + px(root.margin().left(), root.fontSizePx(), root.fontSizePx());
        int contentWidth = Math.max(1, writeWidth - horizontal(root.padding(), root.fontSizePx()));
        int y = px(root.padding().top(), root.fontSizePx(), root.fontSizePx());
        List<BlockBox> boxes = new ArrayList<>(spans.size());
        for (BlockSpan span : spans) {
            boolean source = span.start() == activeStart;
            int level = span.kind() == BlockKind.HEADING ? headingLevel(raw, span) : 0;
            ElementState state = new ElementState(level > 0 ? "h" + level : null, "", false, 0, WRITE_ANCESTORS);
            ComputedStyle style = styles.resolve(span.kind(), List.of(), state);
            int marginTop = px(style.margin().top(), style.fontSizePx(), root.fontSizePx());
            int marginBottom = px(style.margin().bottom(), style.fontSizePx(), root.fontSizePx());
            int marginLeft = px(style.margin().left(), style.fontSizePx(), root.fontSizePx());
            int marginRight = px(style.margin().right(), style.fontSizePx(), root.fontSizePx());
            int blockX = contentX + marginLeft;
            int blockWidth = Math.max(1, contentWidth - marginLeft - marginRight);
            int borderLeft = borderWidth(style.borders().left(), style);
            int borderRight = borderWidth(style.borders().right(), style);
            int borderTop = borderWidth(style.borders().top(), style);
            int borderBottom = borderWidth(style.borders().bottom(), style);
            int innerX = blockX + borderLeft + px(style.padding().left(), style.fontSizePx(), root.fontSizePx());
            int innerWidth = Math.max(1, blockWidth - borderLeft - borderRight
                    - horizontal(style.padding(), style.fontSizePx()));
            int innerY = y + marginTop + borderTop + px(style.padding().top(), style.fontSizePx(), root.fontSizePx());
            int contentEnd = trimmedEnd(raw, span.start(), span.end());
            InlineContent.Style base = new InlineContent.Style(style, styles.font(style,
                    (float) style.fontSizePx()), null, -1, false);
            InlineContent content = content(raw, span, contentEnd, style, base, source, level);
            List<LineBox> lines = new TextMeasurer(innerWidth, base).layout(content, innerX, innerY);
            int linesHeight = lines.stream().mapToInt(LineBox::height).sum();
            if (span.kind() == BlockKind.THEMATIC_BREAK) {
                linesHeight = Math.max(4, linesHeight / 2);
            }
            int height = borderTop + borderBottom + vertical(style.padding(), style.fontSizePx()) + linesHeight;
            BlockBox box = new BlockBox(span, style, lines, source,
                    span.attributes().getOrDefault("info", ""));
            box.place(blockX, y + marginTop, blockWidth, Math.max(1, height), innerX, innerWidth);
            boxes.add(box);
            y += marginTop + height + marginBottom;
        }
        y += px(root.padding().bottom(), root.fontSizePx(), root.fontSizePx());
        return new LayoutResult(document.version(), width, writeX, writeWidth, Math.max(y, 1), boxes);
    }

    private InlineContent content(String raw, BlockSpan span, int end, ComputedStyle style,
                                  InlineContent.Style base, boolean source, int headingLevel) {
        int start = span.start();
        if (source) {
            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, Math.max(12, base.font().getSize()));
            return InlineMarkdown.source(raw, start, end,
                    new InlineContent.Style(base.computed(), mono, null, -1, false));
        }
        return switch (span.kind()) {
            case HEADING -> {
                int contentStart = start;
                while (contentStart < end && raw.charAt(contentStart) == ' ') {
                    contentStart++;
                }
                boolean atx = contentStart < end && raw.charAt(contentStart) == '#';
                if (atx) {
                    contentStart += headingLevel;
                    if (contentStart < end && raw.charAt(contentStart) == ' ') {
                        contentStart++;
                    }
                }
                int contentEnd = atx ? closingHeadingEnd(raw, contentStart, end)
                        : setextHeadingEnd(raw, start, end);
                InlineContent content = InlineMarkdown.render(raw, contentStart, contentEnd, base, styles);
                InlineContent mapped = new InlineContent();
                mapped.append(content, start, contentEnd);
                yield mapped;
            }
            case BLOCK_QUOTE, CALLOUT -> InlineMarkdown.quote(raw, start, end, base, styles);
            case BULLET_LIST -> InlineMarkdown.list(raw, start, end, base, styles, false);
            case ORDERED_LIST -> InlineMarkdown.list(raw, start, end, base, styles, true);
            case FENCED_CODE -> {
                int codeStart = raw.indexOf('\n', start);
                codeStart = codeStart < 0 || codeStart >= end ? end : codeStart + 1;
                int codeEnd = closingFenceStart(raw, codeStart, end);
                yield InlineMarkdown.code(raw, codeStart, codeEnd, base, styles,
                        span.attributes().getOrDefault("info", ""));
            }
            case INDENTED_CODE, FRONT_MATTER, MATH_BLOCK, TABLE, HTML_BLOCK, HTML_COMMENT ->
                    InlineMarkdown.code(raw, start, end, base, styles, "");
            case THEMATIC_BREAK -> new InlineContent();
            default -> InlineMarkdown.render(raw, start, end, base, styles);
        };
    }

    private ComputedStyle rootStyle() {
        return styles.resolve(BlockKind.DOCUMENT, List.of(),
                new ElementState("div", "write", false, 0, List.of()));
    }

    private boolean validAst() {
        return ast != null && document != null && ast.version() == document.version();
    }

    private int activeBlockStart(List<BlockSpan> spans) {
        if (!validAst()) {
            return spans.getFirst().start();
        }
        for (BlockSpan span : spans) {
            if (caretOffset >= span.start() && (caretOffset < span.end()
                    || caretOffset == document.length() && span.end() == document.length())) {
                return span.start();
            }
        }
        return -1;
    }

    private Graphics2D dimmed(Graphics2D graphics, BlockBox block, ComputedStyle root) {
        return focusMode.isEnabled() && !block.source() ? focusMode.wrap(graphics, root) : graphics;
    }

    private Color selectionColor() {
        String value = rootStyle().variables().get("--select-text-bg-color");
        if (value != null && value.matches("#[0-9a-fA-F]{6}")) {
            return new Color(Integer.parseInt(value.substring(1), 16));
        }
        return new Color(0xb6d7ff);
    }

    private static BlockBox blockAtOffset(LayoutResult result, int offset) {
        BlockBox nearest = result.blocks().getFirst();
        for (BlockBox block : result.blocks()) {
            if (offset >= block.span().start() && (offset < block.span().end()
                    || offset == block.span().end() && block.span().end() == result.blocks().getLast().span().end())) {
                return block;
            }
            if (block.span().start() <= offset) {
                nearest = block;
            }
        }
        return nearest;
    }

    private static LineBox lineAtOffset(BlockBox block, int offset) {
        if (block == null || block.lines().isEmpty()) {
            return null;
        }
        LineBox nearest = block.lines().getFirst();
        for (LineBox line : block.lines()) {
            if (offset >= line.sourceStart() && offset <= line.sourceEnd()) {
                return line;
            }
            if (line.sourceStart() <= offset) {
                nearest = line;
            }
        }
        return offset < block.lines().getFirst().sourceStart() ? block.lines().getFirst() : nearest;
    }

    private static LineBox lineAtY(BlockBox block, int y) {
        LineBox nearest = block.lines().getFirst();
        for (LineBox line : block.lines()) {
            if (y >= line.y() && y < line.y() + line.height()) {
                return line;
            }
            if (line.y() <= y) {
                nearest = line;
            }
        }
        return y < block.lines().getFirst().y() ? block.lines().getFirst() : nearest;
    }

    private static int xInLine(LineBox line, int offset) {
        for (InlineRun run : line.runs()) {
            if (offset >= run.sourceStart() && offset <= run.sourceEnd()) {
                return Math.round(run.caretX(offset));
            }
            if (offset < run.sourceStart()) {
                return run.x();
            }
        }
        return line.endX();
    }

    private static int trimmedEnd(String raw, int start, int end) {
        while (end > start && raw.charAt(end - 1) == '\n') {
            end--;
        }
        return end;
    }

    private static int headingLevel(String raw, BlockSpan span) {
        String attribute = span.attributes().get("level");
        if (attribute != null) {
            try {
                return Math.max(1, Math.min(6, Integer.parseInt(attribute)));
            } catch (NumberFormatException ignored) {
                // Некорректный атрибут стороннего parser-плагина не должен ломать отображение.
            }
        }
        int level = 0;
        int p = span.start();
        while (p < span.end() && raw.charAt(p) == ' ') {
            p++;
        }
        while (p < span.end() && raw.charAt(p) == '#' && level < 6) {
            level++;
            p++;
        }
        return Math.max(1, level);
    }

    private static int setextHeadingEnd(String raw, int start, int end) {
        List<InlineMarkdown.SourceLine> lines = InlineMarkdown.lines(raw, start, end);
        if (lines.size() < 2) {
            return end;
        }
        InlineMarkdown.SourceLine underline = lines.getLast();
        String marker = raw.substring(underline.start(), underline.end()).trim();
        if (!marker.isEmpty() && (marker.chars().allMatch(ch -> ch == '=')
                || marker.chars().allMatch(ch -> ch == '-'))) {
            int contentEnd = underline.start();
            while (contentEnd > start && raw.charAt(contentEnd - 1) == '\n') {
                contentEnd--;
            }
            return contentEnd;
        }
        return end;
    }

    private static int closingHeadingEnd(String raw, int start, int end) {
        int p = end;
        while (p > start && raw.charAt(p - 1) == '#') {
            p--;
        }
        if (p < end && p > start && raw.charAt(p - 1) == ' ') {
            p--;
        }
        return p;
    }

    private static int closingFenceStart(String raw, int start, int end) {
        List<InlineMarkdown.SourceLine> lines = InlineMarkdown.lines(raw, start, end);
        if (lines.size() > 1) {
            InlineMarkdown.SourceLine last = lines.getLast();
            String value = raw.substring(last.start(), last.end()).trim();
            if (value.startsWith("```") || value.startsWith("~~~")) {
                return last.start();
            }
        }
        return end;
    }

    private static int horizontal(CssEdges edges, double fontSize) {
        return px(edges.left(), fontSize, fontSize) + px(edges.right(), fontSize, fontSize);
    }

    private static int vertical(CssEdges edges, double fontSize) {
        return px(edges.top(), fontSize, fontSize) + px(edges.bottom(), fontSize, fontSize);
    }

    private static int borderWidth(CssBorder border, ComputedStyle style) {
        return border.visible() ? Math.max(1, px(border.width(), style.fontSizePx(), style.fontSizePx())) : 0;
    }

    private static int px(CssLength length, double reference, double root) {
        return (int) Math.round(length.toPx(reference, root));
    }
}
