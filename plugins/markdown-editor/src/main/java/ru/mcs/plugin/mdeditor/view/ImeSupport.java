package ru.mcs.plugin.mdeditor.view;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import ru.mcs.plugin.mdeditor.model.Range;

/** Swing InputMethodRequests с committed text в модели и отдельным composed-text overlay. */
final class ImeSupport implements InputMethodRequests, InputMethodListener {

    private final EditorCanvas canvas;
    private final InputController input;
    private String composedText = "";

    ImeSupport(EditorCanvas canvas, InputController input) {
        this.canvas = canvas;
        this.input = input;
    }

    @Override
    public void inputMethodTextChanged(InputMethodEvent event) {
        AttributedCharacterIterator iterator = event.getText();
        if (iterator == null) {
            clear();
            event.consume();
            return;
        }
        StringBuilder text = new StringBuilder();
        for (char ch = iterator.first(); ch != AttributedCharacterIterator.DONE; ch = iterator.next()) {
            text.append(ch);
        }
        int committed = Math.max(0, Math.min(event.getCommittedCharacterCount(), text.length()));
        if (committed > 0) {
            input.replaceSelection(text.substring(0, committed));
        }
        composedText = text.substring(committed);
        canvas.repaint();
        event.consume();
    }

    @Override
    public void caretPositionChanged(InputMethodEvent event) {
        event.consume();
    }

    @Override
    public Rectangle getTextLocation(TextHitInfo offset) {
        return canvas.document() == null ? new Rectangle() : canvas.caretRect(canvas.caretOffset());
    }

    @Override
    public TextHitInfo getLocationOffset(int x, int y) {
        if (canvas.document() == null) {
            return null;
        }
        int offset = canvas.hitTest(new java.awt.Point(x, y));
        return TextHitInfo.leading(offset);
    }

    @Override
    public int getInsertPositionOffset() {
        return canvas.caretOffset();
    }

    @Override
    public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                                                        AttributedCharacterIterator.Attribute[] attributes) {
        if (canvas.document() == null || beginIndex < 0 || endIndex < beginIndex
                || endIndex > canvas.document().length()) {
            return null;
        }
        return attributed(canvas.document().text().subSequence(beginIndex, endIndex).toString());
    }

    @Override
    public int getCommittedTextLength() {
        return canvas.document() == null ? 0 : canvas.document().length();
    }

    @Override
    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
        return null;
    }

    @Override
    public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
        if (canvas.document() == null) {
            return null;
        }
        Range selection = canvas.selectionRange();
        return attributed(canvas.document().text().subSequence(selection.start(), selection.end()).toString());
    }

    String composedText() {
        return composedText;
    }

    void clear() {
        composedText = "";
        canvas.repaint();
    }

    void paint(Graphics2D graphics) {
        if (composedText.isEmpty() || canvas.document() == null) {
            return;
        }
        Rectangle caret = canvas.caretRect(canvas.caretOffset());
        int baseline = caret.y + canvas.textLayout().ascent();
        graphics.setFont(canvas.getFont());
        graphics.setColor(canvas.getForeground());
        graphics.drawString(composedText, caret.x + 2, baseline);
        int width = graphics.getFontMetrics().stringWidth(composedText);
        graphics.drawLine(caret.x + 2, baseline + 2, caret.x + 2 + width, baseline + 2);
    }

    private static AttributedCharacterIterator attributed(String text) {
        return text.isEmpty() ? null : new AttributedString(text).getIterator();
    }
}
