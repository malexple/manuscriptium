package ru.mcs.plugin.mdeditor.view;

import java.awt.Point;
import java.awt.Rectangle;
import ru.mcs.plugin.mdeditor.model.Delete;
import ru.mcs.plugin.mdeditor.model.DocumentChange;
import ru.mcs.plugin.mdeditor.model.DocumentModel;
import ru.mcs.plugin.mdeditor.model.Insert;
import ru.mcs.plugin.mdeditor.model.Range;
import ru.mcs.plugin.mdeditor.model.Replace;

/** Все редактирующие действия клавиатуры, мыши, clipboard и IME проходят через DocumentModel.apply. */
public final class InputController {

    private final EditorCanvas canvas;
    private final ClipboardService clipboard;
    private boolean applyingEdit;
    private Float verticalGoalX;

    InputController(EditorCanvas canvas, ClipboardService clipboard) {
        this.canvas = canvas;
        this.clipboard = clipboard;
    }

    public void insertText(String text) {
        if (document() == null || text == null || text.isEmpty()) {
            return;
        }
        replaceSelection(normalizeLineEndings(text));
    }

    public void replaceSelection(String text) {
        DocumentModel document = document();
        if (document == null || text == null) {
            return;
        }
        Range selection = canvas.selectionRange();
        int newCaret = selection.start() + text.length();
        if (selection.length() == 0) {
            if (text.isEmpty()) {
                return;
            }
            apply(new Insert(selection.start(), text));
        } else {
            apply(Replace.of(selection.start(), selection.end(), text));
        }
        moveCaret(newCaret, false);
    }

    public void deleteBackward() {
        DocumentModel document = document();
        if (document == null) {
            return;
        }
        Range selection = canvas.selectionRange();
        if (selection.length() != 0) {
            replaceSelection("");
            return;
        }
        int caret = canvas.caretOffset();
        if (caret == 0) {
            return;
        }
        int start = Character.offsetByCodePoints(document.text(), caret, -1);
        apply(Delete.of(start, caret));
        moveCaret(start, false);
    }

    public void deleteForward() {
        DocumentModel document = document();
        if (document == null) {
            return;
        }
        Range selection = canvas.selectionRange();
        if (selection.length() != 0) {
            replaceSelection("");
            return;
        }
        int caret = canvas.caretOffset();
        if (caret >= document.length()) {
            return;
        }
        int end = Character.offsetByCodePoints(document.text(), caret, 1);
        apply(Delete.of(caret, end));
        moveCaret(caret, false);
    }

    public void deletePreviousWord() {
        if (canvas.selectionRange().length() != 0) {
            replaceSelection("");
            return;
        }
        int end = canvas.caretOffset();
        int start = previousWordOffset(document(), end);
        if (start < end) {
            apply(Delete.of(start, end));
            moveCaret(start, false);
        }
    }

    public void deleteNextWord() {
        if (canvas.selectionRange().length() != 0) {
            replaceSelection("");
            return;
        }
        int start = canvas.caretOffset();
        int end = nextWordOffset(document(), start);
        if (start < end) {
            apply(Delete.of(start, end));
            moveCaret(start, false);
        }
    }

    public void moveLeft(boolean extendSelection, boolean byWord) {
        DocumentModel document = document();
        if (document == null) {
            return;
        }
        Range selection = canvas.selectionRange();
        if (!extendSelection && selection.length() != 0) {
            moveCaret(selection.start(), false);
            return;
        }
        int caret = canvas.caretOffset();
        int target = byWord ? previousWordOffset(document, caret)
                : caret == 0 ? 0 : Character.offsetByCodePoints(document.text(), caret, -1);
        moveCaret(Math.max(0, target), extendSelection);
    }

    public void moveRight(boolean extendSelection, boolean byWord) {
        DocumentModel document = document();
        if (document == null) {
            return;
        }
        Range selection = canvas.selectionRange();
        if (!extendSelection && selection.length() != 0) {
            moveCaret(selection.end(), false);
            return;
        }
        int caret = canvas.caretOffset();
        int target = byWord ? nextWordOffset(document, caret)
                : caret >= document.length() ? document.length()
                : Character.offsetByCodePoints(document.text(), caret, 1);
        moveCaret(Math.min(document.length(), target), extendSelection);
    }

    public void moveVertical(int direction, boolean extendSelection) {
        if (document() == null) {
            return;
        }
        Rectangle caret = canvas.caretRect(canvas.caretOffset());
        float x = verticalGoalX != null ? verticalGoalX : caret.x;
        verticalGoalX = x;
        int y = caret.y + canvas.textLayout().lineHeight() / 2
                + direction * canvas.textLayout().lineHeight();
        moveCaretPreservingGoal(canvas.hitTest(new Point(Math.round(x), y)), extendSelection);
    }

    public void moveHome(boolean extendSelection, boolean documentStart) {
        if (document() == null) {
            return;
        }
        moveCaret(documentStart ? 0 : canvas.textLayout().homeOffset(canvas.caretOffset()), extendSelection);
    }

    public void moveEnd(boolean extendSelection, boolean documentEnd) {
        if (document() == null) {
            return;
        }
        moveCaret(documentEnd ? document().length() : canvas.textLayout().endOffset(canvas.caretOffset()), extendSelection);
    }

    public void movePage(int direction, boolean extendSelection) {
        if (document() == null) {
            return;
        }
        Rectangle caret = canvas.caretRect(canvas.caretOffset());
        int page = Math.max(canvas.textLayout().lineHeight(), canvas.visiblePageHeight()
                - canvas.textLayout().lineHeight());
        int y = caret.y + canvas.textLayout().lineHeight() / 2 + direction * page;
        moveCaret(canvas.hitTest(new Point(caret.x, y)), extendSelection);
    }

    public void selectAll() {
        if (document() != null) {
            setSelection(0, document().length());
        }
    }

    public void copy() {
        String selected = selectedText();
        if (!selected.isEmpty()) {
            clipboard.copy(selected);
        }
    }

    public void cut() {
        String selected = selectedText();
        if (!selected.isEmpty()) {
            clipboard.copy(selected);
            replaceSelection("");
        }
    }

    public void paste() {
        clipboard.paste().ifPresent(this::insertText);
    }

    public void undo() {
        if (document() != null && document().undo().canUndo()) {
            canvas.clearComposition();
            document().undo().undo();
        }
    }

    public void redo() {
        if (document() != null && document().undo().canRedo()) {
            canvas.clearComposition();
            document().undo().redo();
        }
    }

    void selectWord(int offset) {
        Range word = wordRange(document(), offset);
        setSelection(word.start(), word.end());
    }

    void selectLine(int offset) {
        int line = canvas.textLayout().logicalLineAt(offset);
        setSelection(canvas.textLayout().logicalLineStart(line), canvas.textLayout().nextLogicalLineStart(line));
    }

    void documentChanged(DocumentChange change) {
        verticalGoalX = null;
        if (!applyingEdit) {
            int caret = Math.min(document().length(), change.start() + change.newLen());
            canvas.setCaretState(caret, caret);
        }
        canvas.ensureCaretVisible();
    }

    private void setSelection(int anchor, int caret) {
        canvas.setCaretState(caret, anchor);
        verticalGoalX = null;
        canvas.clearComposition();
        canvas.ensureCaretVisible();
        canvas.repaint();
    }

    private void moveCaret(int offset, boolean extendSelection) {
        verticalGoalX = null;
        moveCaretPreservingGoal(offset, extendSelection);
    }

    private void moveCaretPreservingGoal(int offset, boolean extendSelection) {
        int anchor = extendSelection ? canvas.selectionAnchor() : offset;
        canvas.setCaretState(offset, anchor);
        canvas.clearComposition();
        canvas.ensureCaretVisible();
        canvas.repaint();
    }

    private String selectedText() {
        DocumentModel document = document();
        if (document == null) {
            return "";
        }
        Range selection = canvas.selectionRange();
        return document.text().subSequence(selection.start(), selection.end()).toString();
    }

    private void apply(ru.mcs.plugin.mdeditor.model.EditCommand command) {
        applyingEdit = true;
        try {
            document().apply(command);
        } finally {
            applyingEdit = false;
        }
    }

    private DocumentModel document() {
        return canvas.document();
    }

    private static Range wordRange(DocumentModel document, int offset) {
        if (document == null || document.length() == 0) {
            return new Range(0, 0);
        }
        int safe = Math.max(0, Math.min(offset, document.length() - 1));
        int codePoint = Character.codePointAt(document.text(), safe);
        int start = safe;
        int end = safe + Character.charCount(codePoint);
        if (Character.isLetterOrDigit(codePoint) || codePoint == '_') {
            while (start > 0) {
                int previous = Character.codePointBefore(document.text(), start);
                if (!Character.isLetterOrDigit(previous) && previous != '_') {
                    break;
                }
                start -= Character.charCount(previous);
            }
            while (end < document.length()) {
                int next = Character.codePointAt(document.text(), end);
                if (!Character.isLetterOrDigit(next) && next != '_') {
                    break;
                }
                end += Character.charCount(next);
            }
        }
        return new Range(start, end);
    }

    private static int previousWordOffset(DocumentModel document, int offset) {
        if (document == null) {
            return 0;
        }
        int current = Math.max(0, Math.min(offset, document.length()));
        while (current > 0) {
            int cp = Character.codePointBefore(document.text(), current);
            if (!Character.isWhitespace(cp)) {
                break;
            }
            current -= Character.charCount(cp);
        }
        while (current > 0) {
            int cp = Character.codePointBefore(document.text(), current);
            if (Character.isWhitespace(cp)) {
                break;
            }
            current -= Character.charCount(cp);
        }
        return current;
    }

    private static int nextWordOffset(DocumentModel document, int offset) {
        if (document == null) {
            return 0;
        }
        int current = Math.max(0, Math.min(offset, document.length()));
        while (current < document.length()) {
            int cp = Character.codePointAt(document.text(), current);
            if (Character.isWhitespace(cp)) {
                break;
            }
            current += Character.charCount(cp);
        }
        while (current < document.length()) {
            int cp = Character.codePointAt(document.text(), current);
            if (!Character.isWhitespace(cp)) {
                break;
            }
            current += Character.charCount(cp);
        }
        return current;
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
