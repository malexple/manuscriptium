package ru.mcs.plugin.mdeditor.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.im.InputMethodRequests;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ru.mcs.plugin.mdeditor.model.DocumentChange;
import ru.mcs.plugin.mdeditor.model.DocumentListener;
import ru.mcs.plugin.mdeditor.model.DocumentModel;
import ru.mcs.plugin.mdeditor.model.Range;

/** Собственный lightweight Source-редактор: построчный рендер, ввод, selection, caret, IME и clipboard. */
public final class EditorCanvas extends JComponent implements Scrollable {

    private final TextLayoutEngine textLayout = new TextLayoutEngine();
    private final ClipboardService clipboardService;
    private final InputController inputController;
    private final CaretPainter caretPainter;
    private final SelectionPainter selectionPainter;
    private final ViewportController viewportController;
    private final ImeSupport imeSupport;
    private final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void changed(DocumentChange change) {
            documentChanged(List.of(change));
        }

        @Override
        public void changed(List<DocumentChange> changes) {
            documentChanged(changes);
        }
    };

    private String status = "";
    private DocumentModel document;
    private int caretOffset;
    private int selectionAnchor;
    private volatile long lastFrameNanos;
    private volatile long paintedFrames;

    public EditorCanvas() {
        this(new ClipboardService());
    }

    EditorCanvas(ClipboardService clipboardService) {
        this.clipboardService = clipboardService;
        setName("markdown.editor.canvas");
        setFocusable(true);
        setOpaque(true);
        setPreferredSize(new Dimension(800, 600));
        Color background = UIManager.getColor("EditorPane.background");
        setBackground(background == null ? Color.WHITE : background);
        Color foreground = UIManager.getColor("EditorPane.foreground");
        setForeground(foreground == null ? Color.DARK_GRAY : foreground);
        Font font = UIManager.getFont("EditorPane.font");
        setFont(font == null ? new Font(Font.MONOSPACED, Font.PLAIN, 13)
                : new Font(Font.MONOSPACED, Font.PLAIN, Math.max(12, font.getSize())));

        inputController = new InputController(this, clipboardService);
        caretPainter = new CaretPainter(this);
        selectionPainter = new SelectionPainter(this);
        viewportController = new ViewportController(this);
        imeSupport = new ImeSupport(this, inputController);
        MouseController mouseController = new MouseController(this, inputController);
        addMouseListener(mouseController);
        addMouseMotionListener(mouseController);
        new KeyBindingResolver().install(this, inputController);
        enableInputMethods(true);
        addInputMethodListener(imeSupport);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                char ch = event.getKeyChar();
                if (document != null && !Character.isISOControl(ch) && !event.isMetaDown()
                        && (!event.isControlDown() || event.isAltDown())) {
                    inputController.insertText(String.valueOf(ch));
                    event.consume();
                }
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                caretPainter.focusChanged(true);
            }

            @Override
            public void focusLost(FocusEvent event) {
                clearComposition();
                caretPainter.focusChanged(false);
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                textLayout.setWidth(getWidth());
                revalidate();
                repaint();
            }
        });
    }

    public void setDocument(DocumentModel document) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Editor document must be attached on EDT");
        }
        if (this.document != null) {
            this.document.removeListener(documentListener);
        }
        this.document = document;
        caretOffset = 0;
        selectionAnchor = 0;
        clearComposition();
        if (document != null) {
            status = "";
            textLayout.setDocument(document);
            document.addListener(documentListener);
        } else {
            textLayout.clear();
        }
        revalidate();
        repaint();
    }

    public DocumentModel document() {
        return document;
    }

    public InputController input() {
        return inputController;
    }

    ClipboardService clipboardService() {
        return clipboardService;
    }

    public int caretOffset() {
        return caretOffset;
    }

    public void setCaretOffset(int offset) {
        setCaretState(offset, offset);
        clearComposition();
        repaint();
    }

    int selectionAnchor() {
        return selectionAnchor;
    }

    public Range selectionRange() {
        return new Range(Math.min(selectionAnchor, caretOffset), Math.max(selectionAnchor, caretOffset));
    }

    void setCaretState(int offset, int anchor) {
        if (document == null) {
            return;
        }
        if (offset < 0 || offset > document.length() || anchor < 0 || anchor > document.length()) {
            throw new IndexOutOfBoundsException("Caret/anchor outside document: " + offset + "/" + anchor);
        }
        caretOffset = offset;
        selectionAnchor = anchor;
        caretPainter.documentChanged();
    }

    public int hitTest(Point point) {
        return document == null ? 0 : textLayout.hitTest(point);
    }

    public Rectangle caretRect(int offset) {
        return document == null ? new Rectangle() : textLayout.caretRect(offset);
    }

    TextLayoutEngine textLayout() {
        return textLayout;
    }

    int visiblePageHeight() {
        Rectangle visible = getVisibleRect();
        return visible.height > 0 ? visible.height : getHeight();
    }

    void ensureCaretVisible() {
        if (document == null) {
            return;
        }
        Rectangle caret = caretRect(caretOffset);
        caret.grow(12, 4);
        scrollRectToVisible(caret);
    }

    void clearComposition() {
        if (imeSupport != null) {
            imeSupport.clear();
        }
    }

    public void setStatus(String text) {
        status = text == null ? "" : text;
        repaint();
    }

    public String getStatus() {
        return status;
    }

    public long lastFrameNanos() {
        return lastFrameNanos;
    }

    public long paintedFrames() {
        return paintedFrames;
    }

    public void dispose() {
        if (document != null) {
            document.removeListener(documentListener);
            document = null;
        }
        textLayout.clear();
        caretOffset = 0;
        selectionAnchor = 0;
        caretPainter.dispose();
        viewportController.dispose();
        clearComposition();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        textLayout.setFont(font);
    }

    @Override
    public InputMethodRequests getInputMethodRequests() {
        return imeSupport;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        long started = System.nanoTime();
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            if (document == null) {
                paintStatus(g);
            } else {
                paintDocument(g);
            }
        } finally {
            g.dispose();
            lastFrameNanos = System.nanoTime() - started;
            paintedFrames++;
        }
    }

    private void paintDocument(Graphics2D graphics) {
        Rectangle clip = graphics.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        List<TextLayoutEngine.VisualLine> lines = textLayout.visibleLines(clip);
        selectionPainter.paint(graphics, lines);
        graphics.setColor(getForeground());
        for (TextLayoutEngine.VisualLine line : lines) {
            if (line.text() != null) {
                line.text().draw(graphics, TextLayoutEngine.HORIZONTAL_PADDING,
                        line.y() + textLayout.ascent());
            }
        }
        imeSupport.paint(graphics);
        caretPainter.paint(graphics);
    }

    private void paintStatus(Graphics2D graphics) {
        graphics.setFont(getFont());
        Color disabled = UIManager.getColor("Label.disabledForeground");
        graphics.setColor(disabled == null ? getForeground() : disabled);
        FontMetrics metrics = graphics.getFontMetrics();
        String text = fit(status, metrics, Math.max(0, getWidth() - 40));
        int x = Math.max(20, (getWidth() - metrics.stringWidth(text)) / 2);
        int y = Math.max(metrics.getAscent() + 20, (getHeight() + metrics.getAscent()) / 2);
        graphics.drawString(text, x, y);
    }

    @Override
    public Dimension getPreferredSize() {
        if (document == null) {
            return new Dimension(800, 600);
        }
        return new Dimension(800, textLayout.preferredHeight());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(800, 600);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return textLayout.lineHeight();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(textLayout.lineHeight(), visibleRect.height - textLayout.lineHeight());
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }

    private void documentChanged(List<DocumentChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        if (changes.size() == 1) {
            textLayout.changed(changes.getFirst());
        } else {
            textLayout.reload();
        }
        inputController.documentChanged(changes.getLast());
        caretPainter.documentChanged();
        revalidate();
        repaint();
    }

    private static String fit(String text, FontMetrics metrics, int width) {
        if (text.isEmpty() || metrics.stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
