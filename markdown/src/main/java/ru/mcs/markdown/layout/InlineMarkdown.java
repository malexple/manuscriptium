package ru.mcs.markdown.layout;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import ru.mcs.editor.draw.InlineContent;
import ru.mcs.markdown.reference.CitationIndex;
import ru.mcs.markdown.reference.CrossReferenceIndex;
import ru.mcs.markdown.widget.WidgetRegistry;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.swing.editor.style.EditorStyleService;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleElement;
import ru.mcs.spi.editor.style.TextDecoration;

/** Небольшой source-offset-preserving inline-процессор поверх блочного AST. */
final class InlineMarkdown {

    private static final List<StyleElement> WRITE_ANCESTORS =
            List.of(new StyleElement("div", "write", List.of(), 0));

    private InlineMarkdown() {
    }

    static InlineContent source(String raw, int start, int end, InlineContent.Style style) {
        InlineContent result = new InlineContent();
        result.appendRaw(raw.substring(start, end), start, style);
        return result;
    }

    static InlineContent render(String raw, int start, int end, InlineContent.Style style,
                                EditorStyleService styles) {
        return render(raw, start, end, style, styles, null, CrossReferenceIndex.EMPTY);
    }

    static InlineContent render(String raw, int start, int end, InlineContent.Style style,
                                EditorStyleService styles, WidgetRegistry widgets) {
        return render(raw, start, end, style, styles, widgets, CrossReferenceIndex.EMPTY);
    }

    static InlineContent render(String raw, int start, int end, InlineContent.Style style,
                                EditorStyleService styles, WidgetRegistry widgets, CrossReferenceIndex references) {
        return render(raw, start, end, style, styles, widgets, references, CitationIndex.EMPTY);
    }

    static InlineContent render(String raw, int start, int end, InlineContent.Style style,
                                EditorStyleService styles, WidgetRegistry widgets, CrossReferenceIndex references,
                                CitationIndex citations) {
        InlineContent result = new InlineContent();
        parse(raw, start, end, style, styles, widgets, references, citations, result);
        return result;
    }

    static InlineContent quote(String raw, int start, int end, InlineContent.Style style,
                               EditorStyleService styles) {
        return quote(raw, start, end, style, styles, null, CrossReferenceIndex.EMPTY);
    }

    static InlineContent quote(String raw, int start, int end, InlineContent.Style style,
                               EditorStyleService styles, WidgetRegistry widgets) {
        return quote(raw, start, end, style, styles, widgets, CrossReferenceIndex.EMPTY);
    }

    static InlineContent quote(String raw, int start, int end, InlineContent.Style style,
                               EditorStyleService styles, WidgetRegistry widgets, CrossReferenceIndex references) {
        return quote(raw, start, end, style, styles, widgets, references, CitationIndex.EMPTY);
    }

    static InlineContent quote(String raw, int start, int end, InlineContent.Style style,
                               EditorStyleService styles, WidgetRegistry widgets, CrossReferenceIndex references,
                               CitationIndex citations) {
        InlineContent result = new InlineContent();
        for (SourceLine line : lines(raw, start, end)) {
            int content = line.start();
            while (content < line.end() && raw.charAt(content) == ' ') {
                content++;
            }
            while (content < line.end() && raw.charAt(content) == '>') {
                content++;
                if (content < line.end() && raw.charAt(content) == ' ') {
                    content++;
                }
                while (content < line.end() && raw.charAt(content) == ' ') {
                    content++;
                }
            }
            parse(raw, content, line.end(), style, styles, widgets, references, citations, result);
            appendNewline(raw, line, result, style);
        }
        return result;
    }

    static InlineContent list(String raw, int start, int end, InlineContent.Style style,
                              EditorStyleService styles, boolean ordered) {
        return list(raw, start, end, style, styles, ordered, null, CrossReferenceIndex.EMPTY);
    }

    static InlineContent list(String raw, int start, int end, InlineContent.Style style,
                              EditorStyleService styles, boolean ordered, WidgetRegistry widgets) {
        return list(raw, start, end, style, styles, ordered, widgets, CrossReferenceIndex.EMPTY);
    }

    static InlineContent list(String raw, int start, int end, InlineContent.Style style,
                              EditorStyleService styles, boolean ordered, WidgetRegistry widgets,
                              CrossReferenceIndex references) {
        return list(raw, start, end, style, styles, ordered, widgets, references, CitationIndex.EMPTY);
    }

    static InlineContent list(String raw, int start, int end, InlineContent.Style style,
                              EditorStyleService styles, boolean ordered, WidgetRegistry widgets,
                              CrossReferenceIndex references, CitationIndex citations) {
        InlineContent result = new InlineContent();
        for (SourceLine line : lines(raw, start, end)) {
            int p = line.start();
            int spaces = 0;
            while (p < line.end() && raw.charAt(p) == ' ') {
                spaces++;
                p++;
            }
            int markerStart = p;
            int markerEnd = marker(raw, line, p, ordered);
            if (markerEnd < 0) {
                if (p > line.start()) {
                    result.appendMapped("  ", line.start(), p, style);
                }
                parse(raw, p, line.end(), style, styles, widgets, references, citations, result);
                appendNewline(raw, line, result, style);
                continue;
            }
            int content = markerEnd;
            while (content < line.end() && raw.charAt(content) == ' ') {
                content++;
            }
            int taskOffset = -1;
            if (content + 2 < line.end() && raw.charAt(content) == '['
                    && (raw.charAt(content + 1) == ' ' || raw.charAt(content + 1) == 'x'
                    || raw.charAt(content + 1) == 'X') && raw.charAt(content + 2) == ']') {
                taskOffset = content + 1;
                content += 3;
                if (content < line.end() && raw.charAt(content) == ' ') {
                    content++;
                }
            }
            int level = Math.min(8, spaces / 2);
            result.appendMapped("  ".repeat(level), line.start(), markerStart, style);
            String prefix;
            InlineContent.Style prefixStyle = style;
            if (taskOffset >= 0) {
                boolean done = raw.charAt(taskOffset) != ' ';
                // Три позиции занимает checkbox, четвёртая оставляет видимый зазор перед текстом.
                prefix = "    ";
                prefixStyle = new InlineContent.Style(style.computed(), style.font(), null, taskOffset, done);
            } else if (ordered) {
                prefix = raw.substring(markerStart, markerEnd) + " ";
            } else {
                prefix = switch (style.computed().listStyleType()) {
                    case "circle" -> "◦ ";
                    case "square" -> "▪ ";
                    default -> "• ";
                };
            }
            result.appendMapped(prefix, markerStart, Math.max(markerEnd, taskOffset + 2), prefixStyle);
            parse(raw, content, line.end(), style, styles, widgets, references, citations, result);
            appendNewline(raw, line, result, style);
        }
        return result;
    }

    static InlineContent code(String raw, int start, int end, InlineContent.Style style,
                              EditorStyleService styles, String info) {
        InlineContent result = new InlineContent();
        Set<String> keywords = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
                "final", "finally", "float", "for", "function", "if", "implements", "import", "instanceof",
                "int", "interface", "let", "long", "native", "new", "package", "private", "protected",
                "public", "record", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
                "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while",
                "true", "false", "null");
        boolean dark = darkBackground(style.computed());
        InlineContent.Style keyword = colored(style, new Color(dark ? 0xff7b72 : 0xcf222e));
        InlineContent.Style string = colored(style, new Color(dark ? 0xa5d6ff : 0x0a3069));
        InlineContent.Style comment = colored(style, new Color(dark ? 0x8b949e : 0x6e7781));
        for (SourceLine line : lines(raw, start, end)) {
            int p = line.start();
            while (p < line.end()) {
                char ch = raw.charAt(p);
                if (ch == '/' && p + 1 < line.end() && raw.charAt(p + 1) == '/') {
                    result.appendRaw(raw.substring(p, line.end()), p, comment);
                    p = line.end();
                } else if (ch == '#' && !"java".equalsIgnoreCase(info)) {
                    result.appendRaw(raw.substring(p, line.end()), p, comment);
                    p = line.end();
                } else if (ch == '"' || ch == '\'') {
                    int close = p + 1;
                    while (close < line.end()) {
                        if (raw.charAt(close) == '\\') {
                            close += 2;
                        } else if (raw.charAt(close++) == ch) {
                            break;
                        }
                    }
                    close = Math.min(close, line.end());
                    result.appendRaw(raw.substring(p, close), p, string);
                    p = close;
                } else if (Character.isJavaIdentifierStart(ch)) {
                    int close = p + 1;
                    while (close < line.end() && Character.isJavaIdentifierPart(raw.charAt(close))) {
                        close++;
                    }
                    String word = raw.substring(p, close);
                    result.appendRaw(word, p, keywords.contains(word) ? keyword : style);
                    p = close;
                } else {
                    int close = p + 1;
                    while (close < line.end() && !Character.isJavaIdentifierStart(raw.charAt(close))
                            && raw.charAt(close) != '"' && raw.charAt(close) != '\''
                            && raw.charAt(close) != '#' && raw.charAt(close) != '/') {
                        close++;
                    }
                    result.appendRaw(raw.substring(p, close), p, style);
                    p = close;
                }
            }
            appendNewline(raw, line, result, style);
        }
        return result;
    }

    private static void parse(String raw, int start, int end, InlineContent.Style style,
                              EditorStyleService styles, WidgetRegistry widgets, CrossReferenceIndex references,
                              CitationIndex citations, InlineContent out) {
        int i = start;
        while (i < end) {
            char ch = raw.charAt(i);
            if (ch == '#' && i + 5 < end) {
                int idStart = i + 1;
                int idEnd = idStart;
                while (idEnd < end && (Character.isLetterOrDigit(raw.charAt(idEnd))
                        || raw.charAt(idEnd) == ':' || raw.charAt(idEnd) == '_' || raw.charAt(idEnd) == '.'
                        || raw.charAt(idEnd) == '-')) {
                    idEnd++;
                }
                while (idEnd > idStart && raw.charAt(idEnd - 1) == '.') {
                    idEnd--;
                }
                String label = idEnd > idStart ? references.reference(raw.substring(idStart, idEnd)) : null;
                if (label != null) {
                    out.appendMapped(label, i, idEnd, inlineStyle(styles, BlockKind.LINK_REFERENCE, style));
                    i = idEnd;
                    continue;
                }
            }
            if (ch == '{' && i + 6 < end && raw.charAt(i + 1) == '#') {
                int close = raw.indexOf('}', i + 2);
                if (close > i + 6 && close < end
                        && raw.substring(i + 2, close).matches("(sec|fig|tbl):[A-Za-z0-9_.-]+")) {
                    i = close + 1;
                    continue;
                }
            }
            if (ch == '%' && i + 1 < end && raw.charAt(i + 1) == '%') {
                int close = raw.indexOf("%%", i + 2);
                if (close >= 0 && close < end) {
                    // Obsidian comment не занимает видимого места вне активного source-блока.
                    i = close + 2;
                    continue;
                }
            }
            if (ch == '\\' && i + 1 < end) {
                out.appendMapped(raw.substring(i + 1, i + 2), i, i + 2, style);
                i += 2;
                continue;
            }
            if (ch == '$' && widgets != null && (i + 1 >= end || raw.charAt(i + 1) != '$')) {
                int close = inlineMathEnd(raw, i + 1, end);
                if (close > i + 1) {
                    String latex = raw.substring(i + 1, close);
                    out.appendAtom(widgets.inlineMath(latex, style.font().getSize2D(), style.computed().color()),
                            i, close + 1, style);
                    i = close + 1;
                    continue;
                }
            }
            if (ch == '`') {
                int ticks = runLength(raw, i, end, '`');
                String marker = "`".repeat(ticks);
                int close = raw.indexOf(marker, i + ticks);
                if (close >= 0 && close < end) {
                    InlineContent.Style codeStyle = inlineStyle(styles, BlockKind.CODE, style);
                    InlineContent code = new InlineContent();
                    code.appendMapped(raw.substring(i + ticks, close), i + ticks, close, codeStyle);
                    out.append(code, i, close + ticks);
                    i = close + ticks;
                    continue;
                }
            }
            String paired = pairedMarker(raw, i, end);
            if (paired != null) {
                int close = raw.indexOf(paired, i + paired.length());
                if (close > i + paired.length() && close < end) {
                    InlineContent.Style nestedStyle = switch (paired) {
                        case "**", "__" -> style.withFont(style.font().deriveFont(Font.BOLD));
                        case "~~" -> decorated(style, TextDecoration.LINE_THROUGH);
                        default -> style.withFont(style.font().deriveFont(Font.ITALIC));
                    };
                    InlineContent nested = new InlineContent();
                    parse(raw, i + paired.length(), close, nestedStyle, styles, widgets, references, citations, nested);
                    out.append(nested, i, close + paired.length());
                    i = close + paired.length();
                    continue;
                }
            }
            if (ch == '[' && i + 1 < end && raw.charAt(i + 1) == '@') {
                int close = raw.indexOf(']', i + 2);
                if (close > i + 2 && close < end) {
                    String citation = citations.citation(raw.substring(i, close + 1));
                    if (citation != null) {
                        out.appendMapped(citation, i, close + 1,
                                inlineStyle(styles, BlockKind.LINK_REFERENCE, style));
                        i = close + 1;
                        continue;
                    }
                }
            }
            if ((ch == '[' || ch == '!' && i + 1 < end && raw.charAt(i + 1) == '[')) {
                int labelStart = ch == '!' ? i + 2 : i + 1;
                int labelEnd = raw.indexOf(']', labelStart);
                if (labelEnd > labelStart && labelEnd + 1 < end && raw.charAt(labelEnd + 1) == '(') {
                    int close = raw.indexOf(')', labelEnd + 2);
                    if (close > labelEnd && close < end) {
                        String target = raw.substring(labelEnd + 2, close).trim().split("\\s+", 2)[0];
                        InlineContent.Style linkStyle = inlineStyle(styles, BlockKind.LINK, style);
                        InlineContent label = new InlineContent();
                        if (ch == '!') {
                            label.appendMapped("🖼 " + raw.substring(labelStart, labelEnd), i, close + 1, style);
                        } else {
                            parse(raw, labelStart, labelEnd, new InlineContent.Style(linkStyle.computed(),
                                    linkStyle.font(), target, -1, false), styles, widgets, references, citations, label);
                        }
                        out.append(label, i, close + 1);
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (ch == '[' && i + 1 < end && raw.charAt(i + 1) == '[') {
                int close = raw.indexOf("]]", i + 2);
                if (close > i + 2 && close < end) {
                    String target = raw.substring(i + 2, close);
                    int alias = target.indexOf('|');
                    String visible = alias >= 0 ? target.substring(alias + 1) : target;
                    String destination = alias >= 0 ? target.substring(0, alias) : target;
                    InlineContent.Style link = inlineStyle(styles, BlockKind.WIKILINK, style);
                    InlineContent label = new InlineContent();
                    label.appendMapped(visible, i + 2, close,
                            new InlineContent.Style(link.computed(), link.font(), destination, -1, false));
                    out.append(label, i, close + 2);
                    i = close + 2;
                    continue;
                }
            }
            if (ch == '<' && raw.regionMatches(i, "<u>", 0, 3)) {
                int close = raw.indexOf("</u>", i + 3);
                if (close > i + 3 && close < end) {
                    InlineContent nested = new InlineContent();
                    parse(raw, i + 3, close, decorated(style, TextDecoration.UNDERLINE), styles, widgets, references,
                            citations, nested);
                    out.append(nested, i, close + 4);
                    i = close + 4;
                    continue;
                }
            }
            if (ch == '<') {
                int close = raw.indexOf('>', i + 1);
                if (close > i + 1 && close < end) {
                    String target = raw.substring(i + 1, close);
                    if (target.startsWith("http://") || target.startsWith("https://")) {
                        InlineContent.Style link = inlineStyle(styles, BlockKind.AUTOLINK, style);
                        InlineContent label = new InlineContent();
                        label.appendRaw(target, i + 1,
                                new InlineContent.Style(link.computed(), link.font(), target, -1, false));
                        out.append(label, i, close + 1);
                        i = close + 1;
                        continue;
                    }
                }
            }
            int next = i + Character.charCount(raw.codePointAt(i));
            while (next < end && !startsConstruct(raw, next, end)) {
                next += Character.charCount(raw.codePointAt(next));
            }
            out.appendRaw(raw.substring(i, next), i, style);
            i = next;
        }
    }

    private static boolean startsConstruct(String raw, int offset, int end) {
        char ch = raw.charAt(offset);
        return ch == '\\' || ch == '`' || ch == '$' || ch == '*' || ch == '_' || ch == '~' || ch == '[' || ch == '<'
                || ch == '#' || ch == '{' || ch == '%' && offset + 1 < end && raw.charAt(offset + 1) == '%'
                || ch == '!' && offset + 1 < end && raw.charAt(offset + 1) == '[';
    }

    private static int inlineMathEnd(String raw, int start, int end) {
        for (int index = start; index < end && raw.charAt(index) != '\n' && raw.charAt(index) != '\r'; index++) {
            char character = raw.charAt(index);
            if (character == '\\') {
                index++;
            } else if (character == '$' && index > start && !Character.isWhitespace(raw.charAt(index - 1))
                    && (index + 1 >= end || raw.charAt(index + 1) != '$')) {
                return index;
            }
        }
        return -1;
    }

    private static String pairedMarker(String raw, int offset, int end) {
        for (String marker : List.of("**", "__", "~~", "*", "_")) {
            if (offset + marker.length() <= end && raw.startsWith(marker, offset)) {
                return marker;
            }
        }
        return null;
    }

    private static InlineContent.Style inlineStyle(EditorStyleService styles, BlockKind kind,
                                                   InlineContent.Style base) {
        ComputedStyle resolved = styles.resolve(kind, List.of(),
                new ElementState(null, "", false, 0, WRITE_ANCESTORS));
        Font font = kind == BlockKind.CODE ? styles.font(resolved, base.font().getSize2D()) : base.font();
        return new InlineContent.Style(resolved, font, null, -1, false);
    }

    private static InlineContent.Style decorated(InlineContent.Style base, TextDecoration decoration) {
        Set<TextDecoration> decorations = EnumSet.noneOf(TextDecoration.class);
        decorations.addAll(base.computed().textDecorations());
        decorations.add(decoration);
        ComputedStyle s = base.computed();
        ComputedStyle changed = new ComputedStyle(s.color(), s.backgroundColor(), s.fontFamilies(), s.fontSize(),
                s.fontWeight(), s.italic(), s.lineHeight(), s.textAlign(), decorations, s.margin(), s.padding(),
                s.borders(), s.borderRadius(), s.maxWidth(), s.width(), s.letterSpacing(), s.listStyleType(),
                s.variables());
        return new InlineContent.Style(changed, base.font(), base.linkTarget(), -1, false);
    }

    private static boolean darkBackground(ComputedStyle style) {
        Color background = style.backgroundColor();
        if (background.getAlpha() > 0) {
            return luminance(background) < 140;
        }
        return luminance(style.color()) > 160;
    }

    private static int luminance(Color color) {
        return Math.round(color.getRed() * 0.2126f + color.getGreen() * 0.7152f + color.getBlue() * 0.0722f);
    }

    private static InlineContent.Style colored(InlineContent.Style base, Color color) {
        ComputedStyle s = base.computed();
        ComputedStyle changed = new ComputedStyle(color, s.backgroundColor(), s.fontFamilies(), s.fontSize(),
                s.fontWeight(), s.italic(), s.lineHeight(), s.textAlign(), s.textDecorations(), s.margin(),
                s.padding(), s.borders(), s.borderRadius(), s.maxWidth(), s.width(), s.letterSpacing(),
                s.listStyleType(), s.variables());
        return new InlineContent.Style(changed, base.font(), null, -1, false);
    }

    private static int runLength(String raw, int start, int end, char ch) {
        int result = 0;
        while (start + result < end && raw.charAt(start + result) == ch) {
            result++;
        }
        return result;
    }

    private static int marker(String raw, SourceLine line, int offset, boolean ordered) {
        if (offset >= line.end()) {
            return -1;
        }
        if (!ordered && (raw.charAt(offset) == '-' || raw.charAt(offset) == '+' || raw.charAt(offset) == '*')) {
            return offset + 1 < line.end() && raw.charAt(offset + 1) == ' ' ? offset + 1 : -1;
        }
        if (ordered && Character.isDigit(raw.charAt(offset))) {
            int p = offset;
            while (p < line.end() && Character.isDigit(raw.charAt(p))) {
                p++;
            }
            return p + 1 < line.end() && (raw.charAt(p) == '.' || raw.charAt(p) == ')')
                    && raw.charAt(p + 1) == ' ' ? p + 1 : -1;
        }
        return -1;
    }

    private static void appendNewline(String raw, SourceLine line, InlineContent out, InlineContent.Style style) {
        if (line.nextStart() > line.end()) {
            out.appendRaw("\n", line.end(), style);
        }
    }

    record SourceLine(int start, int end, int nextStart) {
    }

    static List<SourceLine> lines(String raw, int start, int end) {
        List<SourceLine> result = new ArrayList<>();
        int lineStart = start;
        while (lineStart < end) {
            int newline = raw.indexOf('\n', lineStart);
            int lineEnd = newline < 0 || newline >= end ? end : newline;
            int next = lineEnd < end ? lineEnd + 1 : lineEnd;
            result.add(new SourceLine(lineStart, lineEnd, next));
            lineStart = next;
        }
        if (result.isEmpty()) {
            result.add(new SourceLine(start, start, start));
        }
        return result;
    }
}
