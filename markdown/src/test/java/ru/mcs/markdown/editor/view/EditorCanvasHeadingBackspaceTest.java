package ru.mcs.markdown.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;
import ru.mcs.markdown.api.AstIndex;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;
import ru.mcs.markdown.api.MarkdownParser.ParseMetrics;
import ru.mcs.markdown.api.MarkdownParser.ParseResult;
import ru.mcs.markdown.editor.TestEditorStyleService;

class EditorCanvasHeadingBackspaceTest {

    @Test
    void backspaceAtStartOfHeadingTextTurnsHeadingIntoParagraphAndUndoRestoresIt() throws Exception {
        Outcome outcome = backspace("# Title", 2, 0, heading(0, 7, 1));

        assertThat(outcome.text()).isEqualTo("Title");
        assertThat(outcome.caret()).isZero();
        assertThat(outcome.afterUndo()).isEqualTo("# Title");
    }

    @Test
    void backspaceWorksForDeeperLevelsAndForCaretBeforeTheMarker() throws Exception {
        assertThat(backspace("### Title", 4, 0, heading(0, 9, 3)).text()).isEqualTo("Title");
        assertThat(backspace("# Title", 0, 0, heading(0, 7, 1)).text()).isEqualTo("Title");
    }

    @Test
    void backspaceInsideHeadingTextStillDeletesOneCharacter() throws Exception {
        Outcome outcome = backspace("# Title", 4, 0, heading(0, 7, 1));

        assertThat(outcome.text()).isEqualTo("# Ttle");
        assertThat(outcome.caret()).isEqualTo(3);
    }

    @Test
    void backspaceInLaterHeadingKeepsPreviousBlockUntouched() throws Exception {
        String markdown = "Intro\n\n# Title";
        Outcome outcome = backspace(markdown, 9, 0,
                new BlockSpan(BlockKind.PARAGRAPH, 0, 5, 1), heading(7, markdown.length(), 1));

        assertThat(outcome.text()).isEqualTo("Intro\n\nTitle");
        assertThat(outcome.caret()).isEqualTo(7);
    }

    @Test
    void backspaceInEmptyHeadingRemovesTheWholeMarker() throws Exception {
        Outcome outcome = backspace("# ", 2, 0, heading(0, 2, 1));

        assertThat(outcome.text()).isEmpty();
        assertThat(outcome.caret()).isZero();
    }

    @Test
    void setextHeadingStaysUntouched() throws Exception {
        Outcome outcome = backspace("Title\n===", 0, 0, heading(0, 9, 1));

        assertThat(outcome.text()).isEqualTo("Title\n===");
    }

    @Test
    void staleAstFallsBackToOrdinaryBackspace() throws Exception {
        Outcome outcome = backspace("# Title", 2, 1, heading(0, 7, 1));

        assertThat(outcome.text()).isEqualTo("#Title");
    }

    @Test
    void hashLineInsideFencedCodeIsNotAHeading() throws Exception {
        String markdown = "```\n# code\n```";
        Outcome outcome = backspace(markdown, 6, 0, new BlockSpan(BlockKind.FENCED_CODE, 0, markdown.length(), 1));

        assertThat(outcome.text()).isEqualTo("```\n#code\n```");
    }

    private static BlockSpan heading(int start, int end, int level) {
        return new BlockSpan(BlockKind.HEADING, start, end, 1, Map.of("level", Integer.toString(level)));
    }

    private static Outcome backspace(String markdown, int caret, long astVersionShift, BlockSpan... blocks)
            throws Exception {
        AtomicReference<Outcome> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                DocumentModelImpl document = new DocumentModelImpl(markdown,
                        DocumentProperties.utf8(LineEnding.LF, false));
                canvas.setSize(800, 600);
                canvas.setEditorStyles(new TestEditorStyleService());
                canvas.setDocument(document);
                canvas.setAstIndex(ast(markdown, document.version() + astVersionShift, blocks));
                canvas.setCaretOffset(caret);
                canvas.input().deleteBackward();
                String changed = document.text().toString();
                int caretAfter = canvas.caretOffset();
                canvas.input().undo();
                result.set(new Outcome(changed, caretAfter, document.text().toString()));
            } finally {
                canvas.dispose();
            }
        });
        return result.get();
    }

    private static AstIndex ast(String markdown, long version, BlockSpan... blocks) {
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, markdown.length(), 0));
        spans.addAll(List.of(blocks));
        return new AstIndex(new Result(version, spans, new ParseMetrics(blocks.length, 0, true)));
    }

    private record Outcome(String text, int caret, String afterUndo) {
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }
}
