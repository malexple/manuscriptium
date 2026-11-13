package ru.mcs.plugin.mdeditor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.Insert;
import ru.mcs.plugin.mdeditor.model.LineEnding;

class TextLayoutEngineTest {

    @Test
    void caretRectAndHitTestRoundTripAcrossWrappedLines() {
        DocumentModelImpl document = document("one two three four five six\nsecond line\n\nlast");
        TextLayoutEngine layout = layout(document, 100);

        assertThat(layout.visibleLines(new Rectangle(0, 0, 800, 1000))).hasSizeGreaterThan(4);
        for (int offset = 0; offset <= document.length(); offset++) {
            Rectangle caret = layout.caretRect(offset);
            int actual = layout.hitTest(new Point(caret.x, caret.y + layout.lineHeight() / 2));
            assertThat(actual).as("offset %s, caret %s", offset, caret).isEqualTo(offset);
        }
        int wrapped = document.text().toString().indexOf("three");
        assertThat(layout.homeOffset(wrapped)).isGreaterThan(0);
        assertThat(layout.endOffset(wrapped)).isLessThan(document.text().toString().indexOf('\n'));
    }

    @Test
    void editRelayoutsOnlyAffectedPhysicalLines() {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < 100; line++) {
            text.append("line ").append(line).append(" with a few words\n");
        }
        DocumentModelImpl document = document(text.toString());
        TextLayoutEngine layout = layout(document, 130);
        document.addListener(layout::changed);
        List<TextLayoutEngine.VisualLine> before = layout.visibleLines(new Rectangle(0, 0, 800, 10000));
        TextLayoutEngine.VisualLine untouched = before.getLast();

        int offset = document.text().toString().indexOf("line 2 ") + 5;
        document.apply(new Insert(offset, "extra wrapped words "));

        List<TextLayoutEngine.VisualLine> after = layout.visibleLines(new Rectangle(0, 0, 800, 10000));
        assertThat(after.getLast()).isSameAs(untouched);
        assertThat(after.size()).isGreaterThan(before.size());
        assertThat(layout.logicalLineAt(document.length())).isEqualTo(100);
    }

    @Test
    void unicodeAndTrailingEmptyLineHaveValidCaretCoordinates() {
        DocumentModelImpl document = document("A😀B\n");
        TextLayoutEngine layout = layout(document, 500);

        for (int offset = 0; offset <= document.length(); offset++) {
            if (offset > 0 && offset < document.length()
                    && Character.isHighSurrogate(document.text().charAt(offset - 1))
                    && Character.isLowSurrogate(document.text().charAt(offset))) {
                continue;
            }
            Rectangle caret = layout.caretRect(offset);
            assertThat(caret.width).isEqualTo(1);
            assertThat(caret.height).isEqualTo(layout.lineHeight());
            assertThat(layout.hitTest(new Point(caret.x, caret.y + caret.height / 2))).isEqualTo(offset);
        }
        assertThat(layout.caretRect(document.length()).y).isGreaterThan(layout.caretRect(0).y);
    }

    private static TextLayoutEngine layout(DocumentModelImpl document, int width) {
        TextLayoutEngine layout = new TextLayoutEngine();
        layout.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        layout.setWidth(width + TextLayoutEngine.HORIZONTAL_PADDING * 2);
        layout.setDocument(document);
        return layout;
    }

    private static DocumentModelImpl document(String text) {
        return new DocumentModelImpl(text,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
    }
}
