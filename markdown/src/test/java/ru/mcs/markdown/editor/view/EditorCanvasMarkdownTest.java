package ru.mcs.markdown.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.editor.TestEditorStyleService;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.LineEnding;
import ru.mcs.markdown.api.AstIndex;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.BlockSpan;
import ru.mcs.markdown.api.MarkdownParser.ParseMetrics;
import ru.mcs.markdown.api.MarkdownParser.ParseResult;

class EditorCanvasMarkdownTest {

    @Test
    void taskCheckboxChangesExactlyOneSourceCharacter() throws Exception {
        String markdown = "- [ ] task\n\nActive";
        AtomicReference<String> changed = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                DocumentModelImpl document = document(markdown);
                canvas.setSize(800, 600);
                canvas.setEditorStyles(new TestEditorStyleService());
                canvas.setDocument(document);
                canvas.setAstIndex(ast(markdown,
                        new BlockSpan(BlockKind.BULLET_LIST, 0, 10, 1),
                        new BlockSpan(BlockKind.PARAGRAPH, 12, markdown.length(), 1)));
                canvas.setCaretOffset(12);
                Point checkbox = null;
                for (int y = 0; y < 80 && checkbox == null; y++) {
                    for (int x = 0; x < 180; x++) {
                        Point point = new Point(x, y);
                        if (canvas.toggleTaskAt(point)) {
                            checkbox = point;
                            break;
                        }
                    }
                }
                assertThat(checkbox).as("visible task checkbox").isNotNull();
                assertThat(document.text().toString()).isEqualTo("- [x] task\n\nActive");
                changed.set(document.text().toString());
            } finally {
                canvas.dispose();
            }
        });
        assertThat(changed.get()).isEqualTo("- [x] task\n\nActive");
    }

    @Test
    void enterAtEndImmediatelyMovesCaretToEmptyNextLineBeforeAstArrives() throws Exception {
        String markdown = "Body";
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                DocumentModelImpl document = document(markdown);
                canvas.setSize(800, 600);
                canvas.setEditorStyles(new TestEditorStyleService());
                canvas.setDocument(document);
                canvas.setAstIndex(ast(markdown,
                        new BlockSpan(BlockKind.PARAGRAPH, 0, markdown.length(), 1)));
                canvas.setCaretOffset(document.length());
                int previousY = canvas.caretRect(canvas.caretOffset()).y;

                canvas.input().insertText("\n");

                assertThat(document.text().toString()).isEqualTo("Body\n");
                assertThat(canvas.caretOffset()).isEqualTo(document.length());
                assertThat(canvas.caretRect(canvas.caretOffset()).y)
                        .as("caret must move before the asynchronous parser publishes a new AST")
                        .isGreaterThan(previousY);
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void enterAtEndKeepsCaretOnEmptyNextLineAfterAstArrives() throws Exception {
        String markdown = "Body";
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                DocumentModelImpl document = document(markdown);
                canvas.setSize(800, 600);
                canvas.setEditorStyles(new TestEditorStyleService());
                canvas.setDocument(document);
                canvas.setAstIndex(ast(markdown,
                        new BlockSpan(BlockKind.PARAGRAPH, 0, markdown.length(), 1)));
                canvas.setCaretOffset(document.length());
                int previousY = canvas.caretRect(canvas.caretOffset()).y;
                canvas.input().insertText("\n");
                int immediateY = canvas.caretRect(canvas.caretOffset()).y;
                String changed = document.text().toString();

                canvas.setAstIndex(ast(changed, document.version(),
                        new BlockSpan(BlockKind.PARAGRAPH, 0, 4, 1)));

                assertThat(immediateY).isGreaterThan(previousY);
                assertThat(canvas.caretRect(canvas.caretOffset()).y)
                        .as("AST paragraph ends before the trailing newline; caret must remain on the empty line")
                        .isEqualTo(immediateY);
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void enterBetweenParagraphsKeepsCaretOnBlankLineAfterAstArrives() throws Exception {
        String markdown = "one\n\ntwo";
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                DocumentModelImpl document = document(markdown);
                canvas.setSize(800, 600);
                canvas.setEditorStyles(new TestEditorStyleService());
                canvas.setDocument(document);
                canvas.setAstIndex(ast(markdown,
                        new BlockSpan(BlockKind.PARAGRAPH, 0, 3, 1),
                        new BlockSpan(BlockKind.PARAGRAPH, 5, 8, 1)));
                canvas.setCaretOffset(3);
                int previousY = canvas.caretRect(canvas.caretOffset()).y;
                canvas.input().insertText("\n");
                int immediateY = canvas.caretRect(canvas.caretOffset()).y;
                String changed = document.text().toString();

                canvas.setAstIndex(ast(changed, document.version(),
                        new BlockSpan(BlockKind.PARAGRAPH, 0, 3, 1),
                        new BlockSpan(BlockKind.PARAGRAPH, 6, 9, 1)));

                assertThat(changed).isEqualTo("one\n\n\ntwo");
                assertThat(canvas.caretOffset()).isEqualTo(4);
                assertThat(immediateY).isGreaterThan(previousY);
                assertThat(canvas.caretRect(canvas.caretOffset()).y)
                        .as("caret in an AST gap belongs to the new blank physical line")
                        .isEqualTo(immediateY);
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void backgroundThemeReloadInvalidatesLayoutOnEdtAndDisposeUnsubscribes() throws Exception {
        String markdown = "one\n\ntwo";
        TestEditorStyleService styles = new TestEditorStyleService();
        MarkdownCanvas canvas = new MarkdownCanvas();
        SwingUtilities.invokeAndWait(() -> {
            canvas.setSize(800, 600);
            canvas.setEditorStyles(styles);
            canvas.setDocument(document(markdown));
            canvas.setAstIndex(ast(markdown,
                    new BlockSpan(BlockKind.PARAGRAPH, 0, 3, 1),
                    new BlockSpan(BlockKind.PARAGRAPH, 5, markdown.length(), 1)));
        });
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> paint(canvas, image));
        assertThat(canvas.markdownLayoutCount()).isEqualTo(1);
        assertThat(styles.listenerCount()).isEqualTo(1);

        Thread reloader = new Thread(styles::reload, "test-theme-reload");
        reloader.start();
        reloader.join(5000);
        assertThat(reloader.isAlive()).isFalse();
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> paint(canvas, image));
        assertThat(canvas.markdownLayoutCount()).isEqualTo(2);

        SwingUtilities.invokeAndWait(canvas::dispose);
        assertThat(styles.listenerCount()).isZero();
    }

    @Test
    void canvasWithoutStylesKeepsOriginalSourceLayout() throws Exception {
        MarkdownCanvas canvas = new MarkdownCanvas();
        SwingUtilities.invokeAndWait(() -> {
            canvas.setSize(640, 480);
            canvas.setDocument(document("# raw **source**"));
        });
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> paint(canvas, image));

        assertThat(canvas.markdownLayoutCount()).isZero();
        assertThat(canvas.getPreferredSize().height).isPositive();
        SwingUtilities.invokeAndWait(canvas::dispose);
    }

    private static void paint(MarkdownCanvas canvas, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static DocumentModelImpl document(String markdown) {
        return new DocumentModelImpl(markdown, DocumentProperties.utf8(LineEnding.LF, false));
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
