package ru.mcs.plugin.mdeditor;

import java.util.Locale;
import java.util.regex.Pattern;
import ru.mcs.plugin.mdeditor.action.SelectionExpander;
import ru.mcs.plugin.mdeditor.action.WordCount;
import ru.mcs.editor.model.Delete;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.Insert;
import ru.mcs.editor.model.Range;
import ru.mcs.editor.model.Replace;
import ru.mcs.plugin.mdeditor.view.MarkdownCanvas;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockSpan;

/** Явные Markdown-команды меню/keymap. Все изменения идут через DocumentModel.apply. */
final class EditorCommands {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.*?)\\*\\*", Pattern.DOTALL);
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final Pattern STRIKE = Pattern.compile("~~(.*?)~~", Pattern.DOTALL);
    private static final Pattern CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern UNDERLINE = Pattern.compile("<u>(.*?)</u>", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\([^)]+\\)");

    private EditorCommands() {
    }

    static void toggleInline(MarkdownFileEditor editor, String opening, String closing) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        Range selection = canvas.selectionRange();
        String text = document.text().toString();
        if (selection.length() == 0) {
            String markers = opening + closing;
            document.apply(Replace.of(selection.start(), selection.end(), markers));
            canvas.setCaretSelection(selection.start() + opening.length(), selection.start() + opening.length());
            return;
        }
        String selected = text.substring(selection.start(), selection.end());
        boolean selectedWrapped = selected.startsWith(opening) && selected.endsWith(closing)
                && selected.length() >= opening.length() + closing.length();
        boolean surroundingWrapped = selection.start() >= opening.length()
                && selection.end() + closing.length() <= text.length()
                && text.startsWith(opening, selection.start() - opening.length())
                && text.startsWith(closing, selection.end());
        if (selectedWrapped) {
            String unwrapped = selected.substring(opening.length(), selected.length() - closing.length());
            replaceAndSelect(document, canvas, selection, unwrapped, 0, unwrapped.length());
        } else if (surroundingWrapped) {
            Range outer = new Range(selection.start() - opening.length(), selection.end() + closing.length());
            replaceAndSelect(document, canvas, outer, selected, 0, selected.length());
        } else {
            replaceAndSelect(document, canvas, selection, opening + selected + closing,
                    opening.length(), selected.length());
        }
    }

    static void insertLink(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        Range selection = canvas.selectionRange();
        String selected = selectedText(document, selection);
        String label = selected.isEmpty() ? "link text" : selected;
        String replacement = "[" + label + "](https://)";
        document.apply(Replace.of(selection.start(), selection.end(), replacement));
        if (selected.isEmpty()) {
            canvas.setCaretSelection(selection.start() + 1, selection.start() + 1 + label.length());
        } else {
            canvas.setCaretSelection(selection.start() + replacement.length(), selection.start() + replacement.length());
        }
    }

    static void clearFormatting(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        Range range = canvas.selectionRange();
        if (range.length() == 0) {
            range = lineRange(document.text().toString(), range);
        }
        String value = document.text().subSequence(range.start(), range.end()).toString();
        value = BOLD.matcher(value).replaceAll("$1");
        value = ITALIC.matcher(value).replaceAll("$1");
        value = STRIKE.matcher(value).replaceAll("$1");
        value = CODE.matcher(value).replaceAll("$1");
        value = UNDERLINE.matcher(value).replaceAll("$1");
        value = LINK.matcher(value).replaceAll("$1");
        document.apply(Replace.of(range.start(), range.end(), value));
        canvas.setCaretSelection(range.start() + value.length(), range.start() + value.length());
    }

    static void heading(MarkdownFileEditor editor, int level) {
        transformLines(editor, line -> {
            int start = leadingSpaces(line);
            String body = line.substring(start).replaceFirst("^#{1,6}[ \\t]+", "");
            return level == 0 ? line.substring(0, start) + body
                    : line.substring(0, start) + "#".repeat(level) + " " + body;
        });
    }

    static void blockQuote(MarkdownFileEditor editor) {
        transformLines(editor, new PrefixTransformation("> ", "^\\s*>\\s?"));
    }

    static void bulletList(MarkdownFileEditor editor) {
        transformLines(editor, new PrefixTransformation("- ", "^\\s*[-+*]\\s+(?!\\[[ xX]\\]\\s)"));
    }

    static void orderedList(MarkdownFileEditor editor) {
        PrefixTransformation transformation = new PrefixTransformation("", "^\\s*\\d+[.)]\\s+");
        final int[] number = {1};
        transformLines(editor, line -> {
            if (line.isBlank()) {
                return line;
            }
            if (transformation.pattern().matcher(line).find()) {
                return transformation.pattern().matcher(line).replaceFirst("");
            }
            return line.substring(0, leadingSpaces(line)) + number[0]++ + ". " + line.substring(leadingSpaces(line));
        });
    }

    static void taskList(MarkdownFileEditor editor) {
        transformLines(editor, new PrefixTransformation("- [ ] ", "^\\s*[-+*]\\s+\\[[ xX]\\]\\s+"));
    }

    static void codeBlock(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        Range range = canvas.selectionRange();
        if (range.length() == 0) {
            range = lineRange(document.text().toString(), range);
            range = withoutTrailingNewline(document.text().toString(), range);
        }
        String text = document.text().toString();
        boolean insideFence = range.start() >= 4 && range.end() + 4 <= text.length()
                && text.startsWith("```\n", range.start() - 4) && text.startsWith("\n```", range.end());
        String selected = document.text().subSequence(range.start(), range.end()).toString();
        String replacement;
        int contentOffset;
        int contentLength;
        if (insideFence) {
            range = new Range(range.start() - 4, range.end() + 4);
            replacement = selected;
            contentOffset = 0;
            contentLength = selected.length();
        } else if (selected.startsWith("```") && selected.endsWith("```") && selected.length() >= 6) {
            int firstNewline = selected.indexOf('\n');
            int lastNewline = selected.lastIndexOf('\n');
            replacement = firstNewline >= 0 && lastNewline > firstNewline
                    ? selected.substring(firstNewline + 1, lastNewline) : "";
            contentOffset = 0;
            contentLength = replacement.length();
        } else {
            replacement = "```\n" + selected + "\n```";
            contentOffset = 4;
            contentLength = selected.length();
        }
        replaceAndSelect(document, canvas, range, replacement, contentOffset, contentLength);
    }

    static void insertTable(MarkdownFileEditor editor) {
        insertSnippet(editor, "| Left | Right |\n|:-----|------:|\n|      |       |", 2, 6);
    }

    static void insertMathBlock(MarkdownFileEditor editor) {
        insertSnippet(editor, "$$\n\n$$", 3, 3);
    }

    static void insertHorizontalRule(MarkdownFileEditor editor) {
        insertSnippet(editor, "\n---\n", 0, 0);
    }

    static void insertImage(MarkdownFileEditor editor) {
        insertSnippet(editor, "![alt text](image.png)", 2, 10);
    }

    static void deleteBlock(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        Range range = canvas.selectionRange();
        if (range.length() == 0) {
            AstIndex ast = editor.astIndex();
            BlockSpan block = ast != null && ast.version() == document.version()
                    ? ast.blockAt(canvas.caretOffset()).orElse(null) : null;
            range = block == null ? lineRange(document.text().toString(), range)
                    : new Range(block.start(), block.end());
        }
        document.apply(Delete.of(range.start(), range.end()));
        int caret = Math.min(range.start(), document.length());
        canvas.setCaretSelection(caret, caret);
    }

    static void toggleCase(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        Range range = canvas.selectionRange();
        if (range.length() == 0) {
            range = SelectionExpander.wordAt(document.text().toString(), canvas.caretOffset());
        }
        if (range.length() == 0) {
            return;
        }
        String selected = selectedText(document, range);
        String replacement = selected.equals(selected.toUpperCase(Locale.ROOT))
                ? selected.toLowerCase(Locale.ROOT) : selected.toUpperCase(Locale.ROOT);
        if (!replacement.equals(selected)) {
            document.apply(Replace.of(range.start(), range.end(), replacement));
        }
        canvas.setCaretSelection(range.start(), range.start() + replacement.length());
    }

    static void duplicateLine(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        String text = document.text().toString();
        int caret = canvas.caretOffset();
        int lineStart = physicalLineStart(text, caret);
        int contentEnd = physicalLineEnd(text, caret);
        int lineEnd = contentEnd < text.length() ? contentEnd + 1 : contentEnd;
        String line = text.substring(lineStart, lineEnd);
        boolean hasTrailingNewline = lineEnd > contentEnd;
        String insertion = hasTrailingNewline ? line : "\n" + line;
        int insertionOffset = hasTrailingNewline ? lineEnd : contentEnd;
        document.apply(new Insert(insertionOffset, insertion));
        int duplicatedCaret = caret + insertion.length();
        canvas.setCaretSelection(duplicatedCaret, duplicatedCaret);
    }

    static void deleteLine(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        String text = document.text().toString();
        if (text.isEmpty()) {
            return;
        }
        int lineStart = physicalLineStart(text, canvas.caretOffset());
        int contentEnd = physicalLineEnd(text, canvas.caretOffset());
        int lineEnd = contentEnd < text.length() ? contentEnd + 1 : contentEnd;
        Range removal = lineEnd > contentEnd ? new Range(lineStart, lineEnd)
                : new Range(lineStart > 0 ? lineStart - 1 : lineStart, lineEnd);
        document.apply(Delete.of(removal.start(), removal.end()));
        int caret = Math.min(removal.start(), document.length());
        canvas.setCaretSelection(caret, caret);
    }

    static void extendSelection(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        Range expanded = SelectionExpander.extend(editor.document().text().toString(),
                canvas.selectionRange(), canvas.caretOffset());
        canvas.setCaretSelection(expanded.start(), expanded.end());
    }

    static WordCount.Stats wordCount(MarkdownFileEditor editor) {
        MarkdownCanvas canvas = editor.activeCanvas();
        Range selection = canvas.selectionRange();
        String text = selection.length() == 0 ? editor.document().text().toString()
                : selectedText(editor.document(), selection);
        return WordCount.count(text);
    }

    private static void insertSnippet(MarkdownFileEditor editor, String snippet, int selectionStart, int selectionEnd) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        Range selection = canvas.selectionRange();
        document.apply(Replace.of(selection.start(), selection.end(), snippet));
        canvas.setCaretSelection(selection.start() + selectionStart, selection.start() + selectionEnd);
    }

    private static void transformLines(MarkdownFileEditor editor, LineTransformation transformation) {
        MarkdownCanvas canvas = editor.activeCanvas();
        DocumentModel document = editor.document();
        String text = document.text().toString();
        Range range = lineRange(text, canvas.selectionRange());
        String value = text.substring(range.start(), range.end());
        String[] lines = value.split("\\n", -1);
        StringBuilder replaced = new StringBuilder(value.length() + 16);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                replaced.append('\n');
            }
            replaced.append(lines[i].isBlank() ? lines[i] : transformation.apply(lines[i]));
        }
        document.apply(Replace.of(range.start(), range.end(), replaced.toString()));
        canvas.setCaretSelection(range.start(), range.start() + replaced.length());
    }

    private static void replaceAndSelect(DocumentModel document, MarkdownCanvas canvas, Range range,
                                         String replacement, int contentOffset, int contentLength) {
        document.apply(Replace.of(range.start(), range.end(), replacement));
        canvas.setCaretSelection(range.start() + contentOffset, range.start() + contentOffset + contentLength);
    }

    private static Range lineRange(String text, Range selection) {
        int start = Math.max(0, Math.min(selection.start(), text.length()));
        int end = Math.max(start, Math.min(selection.end(), text.length()));
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        if (end > start && end <= text.length() && end > 0 && text.charAt(end - 1) == '\n') {
            // Перевод строки уже принадлежит выбранной physical line.
        } else {
            while (end < text.length() && text.charAt(end) != '\n') {
                end++;
            }
            if (end < text.length()) {
                end++;
            }
        }
        return new Range(start, end);
    }

    private static Range withoutTrailingNewline(String text, Range range) {
        int end = range.end();
        while (end > range.start() && text.charAt(end - 1) == '\n') {
            end--;
        }
        return new Range(range.start(), end);
    }

    private static int physicalLineStart(String text, int offset) {
        int start = Math.max(0, Math.min(offset, text.length()));
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        return start;
    }

    private static int physicalLineEnd(String text, int offset) {
        int end = Math.max(0, Math.min(offset, text.length()));
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        return end;
    }

    private static String selectedText(DocumentModel document, Range selection) {
        return document.text().subSequence(selection.start(), selection.end()).toString();
    }

    private static int leadingSpaces(String line) {
        int offset = 0;
        while (offset < line.length() && (line.charAt(offset) == ' ' || line.charAt(offset) == '\t')) {
            offset++;
        }
        return offset;
    }

    @FunctionalInterface
    private interface LineTransformation {
        String apply(String line);
    }

    private record PrefixTransformation(String prefix, String regex) implements LineTransformation {
        private Pattern pattern() {
            return Pattern.compile(regex);
        }

        @Override
        public String apply(String line) {
            Pattern pattern = pattern();
            if (pattern.matcher(line).find()) {
                return pattern.matcher(line).replaceFirst("");
            }
            return line.substring(0, leadingSpaces(line)) + prefix + line.substring(leadingSpaces(line));
        }
    }
}
