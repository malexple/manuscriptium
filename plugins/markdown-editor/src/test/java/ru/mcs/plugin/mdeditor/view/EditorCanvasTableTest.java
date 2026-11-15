package ru.mcs.plugin.mdeditor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser.ParseMetrics;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;

class EditorCanvasTableTest {

    @Test
    void operationsReplaceOnlyTableSourceAndPreserveOtherDocumentBytes() throws Exception {
        String prefix = "# Title\n\nBefore **unchanged**.\n\n";
        String table = "| A | B |\n|:---|---:|\n| one | two |";
        String suffix = "\n\nAfter <!-- unchanged -->.\n";
        String markdown = prefix + table + suffix;
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                DocumentModelImpl document = new DocumentModelImpl(markdown,
                        new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
                canvas.setDocument(document);
                canvas.setAstIndex(ast(markdown, new BlockSpan(BlockKind.TABLE, prefix.length(),
                        prefix.length() + table.length(), 1)));
                int caret = prefix.length() + table.indexOf("one") + 1;
                canvas.setCaretSelection(caret, caret);

                assertThat(canvas.addTableRow()).isTrue();
                String changed = document.text().toString();
                assertThat(changed).startsWith(prefix);
                assertThat(changed).endsWith(suffix);
                assertThat(changed.substring(prefix.length(), changed.length() - suffix.length()))
                        .contains("|  |  |");

                canvas.setAstIndex(ast(changed, document.version(), new BlockSpan(BlockKind.TABLE, prefix.length(),
                        changed.length() - suffix.length(), 1)));
                assertThat(canvas.addTableColumn()).isTrue();
                changed = document.text().toString();
                canvas.setAstIndex(ast(changed, document.version(), new BlockSpan(BlockKind.TABLE, prefix.length(),
                        changed.length() - suffix.length(), 1)));
                assertThat(canvas.clearTableCell()).isTrue();
                assertThat(document.text().toString()).startsWith(prefix);
                assertThat(document.text().toString()).endsWith(suffix);
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void tabMovesBetweenCellsWithoutChangingMarkdown() throws Exception {
        String markdown = "| A | B |\n|---|---|\n| one | two |";
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                DocumentModelImpl document = new DocumentModelImpl(markdown,
                        new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
                canvas.setDocument(document);
                canvas.setAstIndex(ast(markdown, new BlockSpan(BlockKind.TABLE, 0, markdown.length(), 1)));
                canvas.setCaretSelection(markdown.indexOf("one"), markdown.indexOf("one"));
                int before = canvas.caretOffset();
                assertThat(canvas.navigateTable(1)).isTrue();
                assertThat(canvas.caretOffset()).isGreaterThan(before);
                assertThat(document.text().toString()).isEqualTo(markdown);
                assertThat(canvas.navigateTable(-1)).isTrue();
                assertThat(canvas.caretOffset()).isEqualTo(before);
            } finally {
                canvas.dispose();
            }
        });
    }

    private static AstIndex ast(String markdown, BlockSpan... blocks) {
        return ast(markdown, 0, blocks);
    }

    private static AstIndex ast(String markdown, long version, BlockSpan... blocks) {
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, markdown.length(), 0));
        spans.addAll(List.of(blocks));
        return new AstIndex(new Result(version, spans, new ParseMetrics(blocks.length, 0, true)));
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
