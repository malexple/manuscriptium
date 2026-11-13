package ru.mcs.plugin.mdeditor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.text.AttributedString;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;

class EditorCanvasInteractionTest {

    @Test
    void typingSelectionClipboardAndUndoRedoShareDocumentModel() throws Exception {
        EditorCanvas canvas = canvas("one two", new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                canvas.setCaretOffset(3);
                canvas.input().insertText(",");
                assertThat(canvas.document().text().toString()).isEqualTo("one, two");
                assertThat(canvas.caretOffset()).isEqualTo(4);

                canvas.setCaretState(8, 5);
                canvas.input().cut();
                assertThat(canvas.document().text().toString()).isEqualTo("one, ");
                canvas.input().paste();
                assertThat(canvas.document().text().toString()).isEqualTo("one, two");

                canvas.input().undo();
                assertThat(canvas.document().text().toString()).isEqualTo("one, ");
                canvas.input().redo();
                assertThat(canvas.document().text().toString()).isEqualTo("one, two");

                canvas.input().selectAll();
                canvas.clipboardService().copy("A\r\nB\rC");
                canvas.input().paste();
                assertThat(canvas.document().text().toString()).isEqualTo("A\nB\nC");
            });
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void keyboardActionsSupportSelectionUndoAndPlatformMasks() throws Exception {
        EditorCanvas canvas = canvas("abc", new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                canvas.setCaretOffset(3);
                KeyStroke undoKey = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
                Object actionName = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(undoKey);
                assertThat(actionName).isNotNull();
                canvas.input().insertText("d");
                Action undo = canvas.getActionMap().get(actionName);
                undo.actionPerformed(null);
                assertThat(canvas.document().text().toString()).isEqualTo("abc");

                canvas.input().moveLeft(true, false);
                assertThat(canvas.selectionRange().length()).isEqualTo(1);
                canvas.input().moveHome(true, false);
                assertThat(canvas.selectionRange().start()).isZero();
            });
            KeyBindingResolver windows = new KeyBindingResolver(InputEvent.CTRL_DOWN_MASK, false);
            KeyBindingResolver mac = new KeyBindingResolver(InputEvent.META_DOWN_MASK, true);
            assertThat(windows.shortcutMask()).isEqualTo(InputEvent.CTRL_DOWN_MASK);
            assertThat(windows.wordMask()).isEqualTo(InputEvent.CTRL_DOWN_MASK);
            assertThat(mac.shortcutMask()).isEqualTo(InputEvent.META_DOWN_MASK);
            assertThat(mac.wordMask()).isEqualTo(InputEvent.ALT_DOWN_MASK);
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void mouseSupportsDragWordAndPhysicalLineSelection() throws Exception {
        EditorCanvas canvas = canvas("alpha beta\ngamma delta\nthird", new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                Point beta = center(canvas.caretRect(7));
                canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, beta, 2));
                assertThat(selectedText(canvas)).isEqualTo("beta");

                Point gamma = center(canvas.caretRect(13));
                canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, gamma, 3));
                assertThat(selectedText(canvas)).isEqualTo("gamma delta\n");

                Point start = center(canvas.caretRect(0));
                canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, start, 1));
                canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_DRAGGED, center(canvas.caretRect(6)), 1));
                assertThat(canvas.selectionRange().start()).isZero();
                assertThat(canvas.selectionRange().end()).isEqualTo(6);
            });
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void navigationCoversVerticalMovementPagesAndDocumentEdges() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < 100; line++) {
            text.append("line ").append(line).append('\n');
        }
        EditorCanvas canvas = canvas(text.toString(), new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                canvas.setCaretOffset(0);
                canvas.input().moveVertical(1, false);
                assertThat(canvas.caretOffset()).isGreaterThan(0);
                canvas.input().moveHome(false, true);
                canvas.input().movePage(1, false);
                assertThat(canvas.caretOffset()).isGreaterThan(0);
                canvas.input().movePage(1, true);
                assertThat(canvas.selectionRange().length()).isPositive();
                canvas.input().moveEnd(false, true);
                assertThat(canvas.caretOffset()).isEqualTo(canvas.document().length());
                canvas.input().moveHome(false, true);
                assertThat(canvas.caretOffset()).isZero();
                canvas.input().moveRight(false, true);
                assertThat(canvas.caretOffset()).isEqualTo(5);
                canvas.input().moveRight(false, true);
                assertThat(canvas.caretOffset()).isEqualTo(7);
            });
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void backspaceDeletesWholeUnicodeCodePoint() throws Exception {
        EditorCanvas canvas = canvas("A😀B", new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                canvas.setCaretOffset(3);
                canvas.input().deleteBackward();
                assertThat(canvas.document().text().toString()).isEqualTo("AB");
                assertThat(canvas.caretOffset()).isEqualTo(1);
            });
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void imeCommitsTextWithoutAddingCompositionToDocument() throws Exception {
        EditorCanvas canvas = canvas("ab", new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                canvas.setCaretOffset(2);
                AttributedString text = new AttributedString("かな");
                InputMethodEvent event = new InputMethodEvent(canvas, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        text.getIterator(), 1, null, null);
                for (java.awt.event.InputMethodListener listener : canvas.getInputMethodListeners()) {
                    listener.inputMethodTextChanged(event);
                }
                assertThat(canvas.document().text().toString()).isEqualTo("abか");
                assertThat(((ImeSupport) canvas.getInputMethodRequests()).composedText()).isEqualTo("な");
                assertThat(canvas.getInputMethodRequests().getInsertPositionOffset()).isEqualTo(3);
                assertThat(event.isConsumed()).isTrue();
            });
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void shortDocumentTracksViewportHeightButLongDocumentScrolls() throws Exception {
        EditorCanvas canvas = canvas("short", new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                JScrollPane scrollPane = new JScrollPane(canvas);
                scrollPane.getViewport().setSize(400, 1000);
                assertThat(canvas.getScrollableTracksViewportHeight()).isTrue();
                scrollPane.getViewport().setSize(400, 1);
                assertThat(canvas.getScrollableTracksViewportHeight()).isFalse();
            });
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void mouseWheelScrollsViewportWithoutHeavyweightComponents() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < 200; line++) {
            text.append("line ").append(line).append('\n');
        }
        EditorCanvas canvas = canvas(text.toString(), new ClipboardService(new Clipboard("test")));
        try {
            runEdt(() -> {
                JScrollPane scrollPane = new JScrollPane(canvas);
                scrollPane.setSize(400, 100);
                scrollPane.getViewport().setSize(400, 100);
                canvas.setSize(400, 2000);
                MouseWheelEvent wheel = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL,
                        System.currentTimeMillis(), 0, 0, 0, 0, false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1);
                canvas.dispatchEvent(wheel);
                assertThat(scrollPane.getViewport().getViewPosition().y).isPositive();
                assertThat(canvas.getComponents()).isEmpty();
                assertThat(wheel.isConsumed()).isTrue();
            });
        } finally {
            dispose(canvas);
        }
    }

    @Test
    void scaleTwoPaintingKeepsLogicalCaretMetrics() throws Exception {
        EditorCanvas canvas = canvas("first line\nsecond line", new ClipboardService(new Clipboard("test")));
        try {
            AtomicReference<Rectangle> before = new AtomicReference<>();
            runEdt(() -> before.set(canvas.caretRect(4)));
            BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
            runEdt(() -> {
                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.scale(2, 2);
                    canvas.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                assertThat(canvas.caretRect(4)).isEqualTo(before.get());
                assertThat(canvas.paintedFrames()).isPositive();
                assertThat(canvas.lastFrameNanos()).isPositive();
            });
        } finally {
            dispose(canvas);
        }
    }

    private static EditorCanvas canvas(String text, ClipboardService clipboard) throws Exception {
        AtomicReference<EditorCanvas> result = new AtomicReference<>();
        runEdt(() -> {
            EditorCanvas canvas = new EditorCanvas(clipboard);
            canvas.setSize(400, 200);
            canvas.setDocument(document(text));
            result.set(canvas);
        });
        return result.get();
    }

    private static DocumentModelImpl document(String text) {
        return new DocumentModelImpl(text,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
    }

    private static String selectedText(EditorCanvas canvas) {
        return canvas.document().text().subSequence(canvas.selectionRange().start(), canvas.selectionRange().end()).toString();
    }

    private static Point center(Rectangle rectangle) {
        return new Point(rectangle.x, rectangle.y + rectangle.height / 2);
    }

    private static MouseEvent mouse(EditorCanvas canvas, int id, Point point, int clicks) {
        int modifiers = id == MouseEvent.MOUSE_DRAGGED ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
        return new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, point.x, point.y,
                clicks, false, MouseEvent.BUTTON1);
    }

    private static void dispose(EditorCanvas canvas) throws Exception {
        runEdt(canvas::dispose);
    }

    private static void runEdt(RunnableWithException action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                action.run();
            } catch (Throwable e) {
                error.set(e);
            }
        });
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    @FunctionalInterface
    private interface RunnableWithException {
        void run() throws Exception;
    }
}
