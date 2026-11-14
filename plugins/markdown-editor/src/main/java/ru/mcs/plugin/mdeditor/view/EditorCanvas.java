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
import java.awt.Desktop;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.im.InputMethodRequests;
import java.net.URI;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.model.DocumentChange;
import ru.mcs.plugin.mdeditor.model.DocumentListener;
import ru.mcs.plugin.mdeditor.model.DocumentModel;
import ru.mcs.plugin.mdeditor.model.Range;
import ru.mcs.plugin.mdeditor.model.Replace;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.style.EditorStyleService;

/** Собственный lightweight Source-редактор: построчный рендер, ввод, selection, caret, IME и clipboard. */
public final class EditorCanvas extends JComponent implements Scrollable {

    private final TextLayoutEngine textLayout = new TextLayoutEngine();
    private final MarkdownLayoutEngine markdownLayout = new MarkdownLayoutEngine();
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

    private final Runnable styleListener = this::editorStylesChanged;

    private String status = "";
    private DocumentModel document;
    private AstIndex ast;
    private EditorStyleService styles;
    private boolean disposed;
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
                markdownLayout.setWidth(getWidth());
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
            markdownLayout.setContext(document, ast, styles);
            markdownLayout.setWidth(getWidth());
            document.addListener(documentListener);
        } else {
            textLayout.clear();
            markdownLayout.clear();
        }
        revalidate();
        repaint();
    }

    public DocumentModel document() {
        return document;
    }

    public void setEditorStyles(EditorStyleService styles) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Editor styles must be attached on EDT");
        }
        if (this.styles != null) {
            this.styles.removeChangeListener(styleListener);
        }
        this.styles = styles;
        if (styles != null) {
            styles.addChangeListener(styleListener);
        }
        markdownLayout.setContext(document, ast, styles);
        revalidate();
        repaint();
    }

    private void editorStylesChanged() {
        if (SwingUtilities.isEventDispatchThread()) {
            refreshEditorStyles();
        } else {
            SwingUtilities.invokeLater(this::refreshEditorStyles);
        }
    }

    private void refreshEditorStyles() {
        if (disposed || styles == null) {
            return;
        }
        markdownLayout.setContext(document, ast, styles);
        revalidate();
        repaint();
    }

    public void setAstIndex(AstIndex ast) {
        this.ast = ast;
        markdownLayout.setAst(ast);
        revalidate();
        repaint();
    }

    public long markdownLayoutCount() {
        return markdownLayout.layoutCount();
    }

    private boolean markdownMode() {
        return document != null && styles != null;
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
        markdownLayout.setCaretOffset(offset);
        caretPainter.documentChanged();
        revalidate();
    }

    public int hitTest(Point point) {
        return document == null ? 0 : markdownMode() ? markdownLayout.hitTest(point) : textLayout.hitTest(point);
    }

    public Rectangle caretRect(int offset) {
        return document == null ? new Rectangle() : markdownMode()
                ? markdownLayout.caretRect(offset) : textLayout.caretRect(offset);
    }

    Color caretColor() {
        return markdownMode() ? markdownLayout.foreground() : getForeground();
    }

    int lineHeight() {
        return markdownMode() ? markdownLayout.lineHeightAt(caretOffset) : textLayout.lineHeight();
    }

    int homeOffset(int offset) {
        return markdownMode() ? markdownLayout.homeOffset(offset) : textLayout.homeOffset(offset);
    }

    int endOffset(int offset) {
        return markdownMode() ? markdownLayout.endOffset(offset) : textLayout.endOffset(offset);
    }

    boolean toggleTaskAt(Point point) {
        if (!markdownMode()) {
            return false;
        }
        int offset = markdownLayout.taskCheckboxAt(point);
        if (offset < 0 || offset >= document.length()) {
            return false;
        }
        char current = document.text().charAt(offset);
        if (current != ' ' && current != 'x' && current != 'X') {
            return false;
        }
        document.apply(Replace.of(offset, offset + 1, current == ' ' ? "x" : " "));
        setCaretState(offset + 1, offset + 1);
        ensureCaretVisible();
        repaint();
        return true;
    }

    boolean openLinkAt(Point point) {
        if (!markdownMode()) {
            return false;
        }
        String target = markdownLayout.linkAt(point);
        if (target == null || !(target.startsWith("https://") || target.startsWith("http://"))
                || !Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return false;
        }
        try {
            URI uri = URI.create(target);
            Thread opener = new Thread(() -> browse(uri), "manuscriptium-link-opener");
            opener.setDaemon(true);
            opener.start();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void browse(URI uri) {
        try {
            Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            System.getLogger(EditorCanvas.class.getName()).log(System.Logger.Level.WARNING,
                    "Cannot open link " + uri, e);
        }
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
        if (disposed) {
            return;
        }
        disposed = true;
        if (styles != null) {
            styles.removeChangeListener(styleListener);
            styles = null;
        }
        if (document != null) {
            document.removeListener(documentListener);
            document = null;
        }
        textLayout.clear();
        markdownLayout.clear();
        ast = null;
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
            g.setColor(markdownMode() ? markdownLayout.background() : getBackground());
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
        if (markdownMode()) {
            markdownLayout.paint(graphics, clip, selectionRange());
            imeSupport.paint(graphics);
            caretPainter.paint(graphics);
            return;
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
        return new Dimension(800, markdownMode() ? markdownLayout.layout().height() : textLayout.preferredHeight());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(800, 600);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return lineHeight();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        int lineHeight = lineHeight();
        return Math.max(lineHeight, visibleRect.height - lineHeight);
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
        if (markdownMode()) {
            ast = null;
            markdownLayout.setAst(null);
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
