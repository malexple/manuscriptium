package ru.mcs.plugin.mdparser;

import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.util.ast.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;

/** Преобразует Flexmark Node в parser-neutral spans, сохраняя исходные offsets каждого узла. */
final class NodeSpanMapper {

    private NodeSpanMapper() {
    }

    static List<BlockSpan> tree(Node node, String source, int baseOffset, int depth) {
        List<BlockSpan> result = new ArrayList<>();
        append(node, source, baseOffset, depth, result);
        return List.copyOf(result);
    }

    private static void append(Node node, String source, int baseOffset, int depth, List<BlockSpan> result) {
        int start = node.getStartOffset();
        int end = node.getEndOffset();
        if (start < 0 || end < start) {
            throw new IllegalStateException("Flexmark node has invalid source offsets: "
                    + node.getClass().getName() + " " + start + ".." + end);
        }
        boolean embed = isEmbed(node, source, start);
        int spanStart = embed ? start - 1 : start;
        result.add(new BlockSpan(embed ? BlockKind.EMBED : kind(node), baseOffset + spanStart,
                baseOffset + end, depth, attributes(node)));
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            append(child, source, baseOffset, depth + 1, result);
        }
    }

    private static boolean isEmbed(Node node, String source, int start) {
        if (!"WikiLink".equals(node.getClass().getSimpleName())
                || start == 0 || source.charAt(start - 1) != '!') {
            return false;
        }
        int backslashes = 0;
        for (int i = start - 2; i >= 0 && source.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 0;
    }

    private static BlockKind kind(Node node) {
        String name = node.getClass().getSimpleName();
        return switch (name) {
            case "Document" -> BlockKind.DOCUMENT;
            case "Heading" -> BlockKind.HEADING;
            case "Paragraph" -> BlockKind.PARAGRAPH;
            case "BlockQuote" -> BlockKind.BLOCK_QUOTE;
            case "BulletList" -> BlockKind.BULLET_LIST;
            case "OrderedList" -> BlockKind.ORDERED_LIST;
            case "BulletListItem", "OrderedListItem", "ListItem" -> BlockKind.LIST_ITEM;
            case "TaskListItem" -> BlockKind.TASK_ITEM;
            case "FencedCodeBlock" -> BlockKind.FENCED_CODE;
            case "IndentedCodeBlock" -> BlockKind.INDENTED_CODE;
            case "HtmlBlock", "HtmlInnerBlock" -> BlockKind.HTML_BLOCK;
            case "HtmlCommentBlock" -> BlockKind.HTML_COMMENT;
            case "ThematicBreak" -> BlockKind.THEMATIC_BREAK;
            case "Reference" -> BlockKind.REFERENCE_DEFINITION;
            case "YamlFrontMatterBlock" -> BlockKind.FRONT_MATTER;
            case "YamlFrontMatterNode" -> BlockKind.FRONT_MATTER_KEY;
            case "YamlFrontMatterValue" -> BlockKind.FRONT_MATTER_VALUE;
            case "FootnoteBlock" -> BlockKind.FOOTNOTE_DEFINITION;
            case "TableBlock" -> BlockKind.TABLE;
            case "TableHead" -> BlockKind.TABLE_HEAD;
            case "TableBody" -> BlockKind.TABLE_BODY;
            case "TableSeparator" -> BlockKind.TABLE_SEPARATOR;
            case "TableRow" -> BlockKind.TABLE_ROW;
            case "TableCell" -> BlockKind.TABLE_CELL;
            case "TableColumnSeparator" -> BlockKind.TABLE_COLUMN_SEPARATOR;
            case "TableCaption" -> BlockKind.TABLE_CAPTION;
            case "Text" -> BlockKind.TEXT;
            case "SoftLineBreak" -> BlockKind.SOFT_BREAK;
            case "HardLineBreak" -> BlockKind.HARD_BREAK;
            case "Emphasis" -> BlockKind.EMPHASIS;
            case "StrongEmphasis" -> BlockKind.STRONG_EMPHASIS;
            case "Strikethrough" -> BlockKind.STRIKETHROUGH;
            case "Subscript" -> BlockKind.SUBSCRIPT;
            case "Code" -> BlockKind.CODE;
            case "Link" -> BlockKind.LINK;
            case "LinkRef" -> BlockKind.LINK_REFERENCE;
            case "Image" -> BlockKind.IMAGE;
            case "ImageRef" -> BlockKind.IMAGE_REFERENCE;
            case "AutoLink" -> BlockKind.AUTOLINK;
            case "MailLink" -> BlockKind.MAIL_LINK;
            case "WikiLink" -> BlockKind.WIKILINK;
            case "WikiImage" -> BlockKind.WIKI_IMAGE;
            case "Footnote" -> BlockKind.FOOTNOTE_REFERENCE;
            case "HtmlInline", "HtmlInlineComment" -> BlockKind.HTML_INLINE;
            case "HtmlEntity" -> BlockKind.HTML_ENTITY;
            default -> BlockKind.OTHER;
        };
    }

    private static Map<String, String> attributes(Node node) {
        Map<String, String> result = new HashMap<>();
        if (node instanceof Heading heading) {
            result.put("level", Integer.toString(heading.getLevel()));
        }
        if (node instanceof FencedCodeBlock code) {
            String info = code.getInfo().toString().trim();
            if (!info.isEmpty()) {
                result.put("info", info);
            }
            result.put("closed", Boolean.toString(!code.getClosingMarker().toString().isEmpty()));
        }
        if (node instanceof TaskListItem task) {
            result.put("done", Boolean.toString(task.isItemDoneMarker()));
        }
        if (node instanceof TableCell cell) {
            result.put("alignment", cell.getAlignment().name().toLowerCase(java.util.Locale.ROOT));
        }
        return result;
    }
}
