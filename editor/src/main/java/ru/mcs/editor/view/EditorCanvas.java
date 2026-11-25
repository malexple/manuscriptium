package ru.mcs.editor.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import ru.mcs.editor.action.EditorContext;
import ru.mcs.editor.mode.TypewriterMode;
import ru.mcs.editor.model.DocumentChange;
import ru.mcs.editor.model.DocumentListener;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.Range;

/** Универсальный lightweight Source-редактор: построчный рендер, ввод, selection, caret, IME и clipboard. */
public class EditorCanvas extends JComponent implements Scrollable, EditorContext {

    private final TextLayoutEngine textLayout = new TextLayoutEngine();
    private final ClipboardService clipboardService;
    private final InputController inputController;
    private final CaretPainter caretPainter;
    private final SelectionPainter selectionPainter;
    private final ViewportController viewportController;
    private final ImeSupport imeSupport;
    protected final TypewriterMode typewriterMode = new TypewriterMode();
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
    protected boolean disposed;
    private boolean nativeImageDropEnabled;
    private int caretOffset;
    private int selectionAnchor;
    private volatile long lastFrameNanos;
    private volatile long paintedFrames;

    public EditorCanvas() {
        this(new ClipboardService());
    }

    protected EditorCanvas(ClipboardService clipboardService) {
        this.clipboardService = clipboardService;
        setName("editor.canvas");
        setFocusable(true);
        setOpaque(true);
        setPreferredSize(new Dimension(800, 600));
        applyLookAndFeelDefaults();

        inputController = new InputController(this, clipboardService);
        caretPainter = new CaretPainter(this);
        selectionPainter = new SelectionPainter(this);
        viewportController = new ViewportController(this);
        imeSupport = new ImeSupport(this, inputController);
        MouseController mouseController = new MouseController(this, inputController);
        addMouseListener(mouseController);
        addMouseMotionListener(mouseController);
        setTransferHandler(new ImageTransferHandler());
        setNativeImageDropEnabled(true);
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
                    afterTextInserted();
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
                onViewportWidthChanged(getWidth());
                fireViewChanged();
                revalidate();
                typewriterMode.centerCaret(EditorCanvas.this);
                repaint();
            }
        });
    }

    /** Цвета и шрифт по умолчанию берутся у текущей темы; явно заданные значения не затрагиваются. */
    private void applyLookAndFeelDefaults() {
        if (!isBackgroundSet() || getBackground() instanceof UIResource) {
            Color background = UIManager.getColor("EditorPane.background");
            setBackground(new ColorUIResource(background == null ? Color.WHITE : background));
        }
        if (!isForegroundSet() || getForeground() instanceof UIResource) {
            Color foreground = UIManager.getColor("EditorPane.foreground");
            setForeground(new ColorUIResource(foreground == null ? Color.DARK_GRAY : foreground));
        }
        if (!isFontSet() || getFont() instanceof UIResource) {
            Font font = UIManager.getFont("EditorPane.font");
            int size = font == null ? 13 : Math.max(12, font.getSize());
            setFont(new FontUIResource(new Font(Font.MONOSPACED, Font.PLAIN, size)));
        }
    }

    /** Вызывается при смене темы (FlatLaf.updateUI): открытые вкладки получают цвета новой темы сразу. */
    @Override
    public void updateUI() {
        super.updateUI();
        applyLookAndFeelDefaults();
        revalidate();
        repaint();
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
        onDocumentContextChanged();
        fireViewChanged();
        revalidate();
        typewriterMode.centerCaret(this);
        repaint();
    }

    @Override
    public JComponent component() {
        return this;
    }

    @Override
    public DocumentModel document() {
        return document;
    }

    /** Вызывается после смены document; реализации языкового содержимого обновляют свой контекст. */
    protected void onDocumentContextChanged() {
    }

    /** Вызывается при изменении ширины viewport. */
    protected void onViewportWidthChanged(int width) {
    }

    /** Вызывается после применения изменений к document. */
    protected void onDocumentContentChanged(List<DocumentChange> changes) {
    }

    /** Вызывается после ввода одного обычного символа с клавиатуры. */
    protected void afterTextInserted() {
    }

    /**
     * Backspace без выделения: вызывается до обычного удаления символа.
     * {@code true} означает, что языковое расширение само обработало клавишу.
     */
    protected boolean beforeDeleteBackward() {
        return false;
    }

    /** Активирует native DnD только для видимой карточки редактора. */
    public void setNativeImageDropEnabled(boolean enabled) {
        if (nativeImageDropEnabled == enabled) {
            return;
        }
        nativeImageDropEnabled = enabled;
        setDropTarget(enabled && !GraphicsEnvironment.isHeadless()
                ? new DropTarget(this, DnDConstants.ACTION_COPY, new ImageDropTargetListener(), true) : null);
    }

    public boolean navigateTable(int direction) {
        return false;
    }

    /** Стрелка влево/вправо на границе ячейки таблицы: перешагивает разделитель {@code |}. */
    public boolean tableStep(int direction) {
        return false;
    }

    /** Backspace (-1) / Delete (+1) на границе ячейки: разделители таблицы не удаляются. */
    public boolean tableBoundary(int direction) {
        return false;
    }

    /** Enter внутри таблицы: ячейка строкой ниже, в последней строке — новая строка. */
    public boolean tableEnter() {
        return false;
    }

    /** Тройной клик: выделить текст текущей ячейки таблицы вместо строки исходника. */
    public boolean selectTableCell() {
        return false;
    }

    /** Нажатие мыши по нарисованной поверх содержимого панели (например, управление таблицей). */
    public boolean pressOverlay(Point point) {
        return false;
    }

    /** Вставка структурированного текста из буфера (например, таблицы из Excel); false — вставить как обычный текст. */
    public boolean pasteStructured(String text) {
        return false;
    }

    /** Перетаскивание после {@link #pressOverlay(Point)} (например, ползунок прокрутки таблицы). */
    public boolean dragOverlay(Point point) {
        return false;
    }

    public void releaseOverlay() {
    }

    /** Колесо мыши над содержимым с собственной прокруткой (широкая таблица); true — событие обработано. */
    public boolean wheelOverlay(java.awt.event.MouseWheelEvent event) {
        return false;
    }

    /** Подготовка вставляемого текста к месту каретки (например, экранирование {@code |} в ячейке таблицы). */
    public String prepareInsertedText(String text) {
        return text;
    }

    public boolean addTableRow() {
        return false;
    }

    public boolean removeTableRow() {
        return false;
    }

    public boolean addTableColumn() {
        return false;
    }

    public boolean removeTableColumn() {
        return false;
    }

    public boolean clearTableCell() {
        return false;
    }

    public String tableCellAtCaret() {
        return null;
    }

    public InputController input() {
        return inputController;
    }

    public void setFocusMode(boolean enabled) {
    }

    public boolean focusMode() {
        return false;
    }

    public void setTypewriterMode(boolean enabled) {
        int removedTopPadding = enabled ? 0 : typewriterMode.topPadding(this);
        typewriterMode.setEnabled(enabled);
        revalidate();
        if (enabled) {
            typewriterMode.centerCaret(this);
        } else {
            typewriterMode.restoreViewport(this, removedTopPadding);
        }
    }

    public boolean typewriterMode() {
        return typewriterMode.isEnabled();
    }

    public void setAutoPairEnabled(boolean enabled) {
        inputController.setAutoPairEnabled(enabled);
    }

    public boolean autoPairEnabled() {
        return inputController.autoPairEnabled();
    }

    /** Точка расширения для языкового контекстного меню. */
    public void showContextMenu(MouseEvent event) {
    }

    public ClipboardService clipboardService() {
        return clipboardService;
    }

    @Override
    public int caretOffset() {
        return caretOffset;
    }

    public void setCaretOffset(int offset) {
        setCaretState(offset, offset);
        clearComposition();
        repaint();
    }

    public void setCaretSelection(int anchor, int caret) {
        setCaretState(caret, anchor);
        clearComposition();
        ensureCaretVisible();
        repaint();
    }

    public int selectionAnchor() {
        return selectionAnchor;
    }

    public Range selectionRange() {
        return new Range(Math.min(selectionAnchor, caretOffset), Math.max(selectionAnchor, caretOffset));
    }

    public void setCaretState(int offset, int anchor) {
        if (document == null) {
            return;
        }
        if (offset < 0 || offset > document.length() || anchor < 0 || anchor > document.length()) {
            throw new IndexOutOfBoundsException("Caret/anchor outside document: " + offset + "/" + anchor);
        }
        caretOffset = offset;
        selectionAnchor = anchor;
        onCaretChanged(offset);
        caretPainter.documentChanged();
        fireViewChanged();
        revalidate();
        typewriterMode.centerCaret(this);
    }

    protected void onCaretChanged(int offset) {
    }

    public int hitTest(Point point) {
        return contentHitTest(contentPoint(point));
    }

    public int contentHitTest(Point point) {
        return document == null ? 0 : textLayout.hitTest(point);
    }

    protected Point contentPoint(Point point) {
        return new Point(point.x, point.y - typewriterMode.topPadding(this));
    }

    @Override
    public Rectangle caretRect(int offset) {
        return document == null ? new Rectangle() : textLayout.caretRect(offset);
    }

    public Rectangle componentCaretRect(int offset) {
        Rectangle caret = caretRect(offset);
        caret.y += typewriterMode.topPadding(this);
        return caret;
    }

    public Color caretColor() {
        return getForeground();
    }

    private SyntaxHighlighter highlighter;
    private TokenColors tokenColors;
    private List<HighlightSpan> highlightSpans = List.of();
    private long highlightVersion = -1;

    /** Включает цветовую подсветку текста; {@code null} отключает её. Меняет только цвет, не шрифт и не раскладку. */
    public void setSyntaxHighlighting(SyntaxHighlighter highlighter, TokenColors colors) {
        this.highlighter = highlighter;
        this.tokenColors = colors;
        highlightSpans = List.of();
        highlightVersion = -1;
        repaint();
    }

    private List<HighlightSpan> currentHighlightSpans() {
        long version = document.version();
        if (version != highlightVersion) {
            try {
                highlightSpans = highlighter.highlight(document.text());
            } catch (RuntimeException failure) {
                highlightSpans = List.of();
            }
            highlightVersion = version;
        }
        return highlightSpans;
    }

    private boolean darkBackground() {
        Color background = getBackground();
        return 0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue() < 128;
    }

    private void paintHighlighted(Graphics2D graphics, TextLayoutEngine.VisualLine line) {
        List<HighlightSpan> spans = currentHighlightSpans();
        Color base = getForeground();
        boolean dark = darkBackground();
        int low = 0;
        int high = spans.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (spans.get(middle).end() <= line.start()) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        java.awt.Shape saved = graphics.getClip();
        int cursor = line.start();
        try {
            for (int index = low; index < spans.size(); index++) {
                HighlightSpan span = spans.get(index);
                if (span.start() >= line.end()) {
                    break;
                }
                int from = Math.max(span.start(), line.start());
                int to = Math.min(span.end(), line.end());
                if (to <= from) {
                    continue;
                }
                if (from > cursor) {
                    paintSegment(graphics, saved, line, cursor, from, base);
                }
                Color color = tokenColors == null ? null : tokenColors.color(span.token(), dark);
                paintSegment(graphics, saved, line, from, to, color == null ? base : color);
                cursor = to;
            }
            if (cursor < line.end()) {
                paintSegment(graphics, saved, line, cursor, line.end(), base);
            }
        } finally {
            graphics.setClip(saved);
        }
    }

    private void paintSegment(Graphics2D graphics, java.awt.Shape saved, TextLayoutEngine.VisualLine line,
                              int from, int to, Color color) {
        float left = TextLayoutEngine.HORIZONTAL_PADDING + textLayout.caretX(line, from)
                - (from == line.start() ? 2 : 0);
        float right = TextLayoutEngine.HORIZONTAL_PADDING + textLayout.caretX(line, to)
                + (to == line.end() ? 2 : 0);
        graphics.setClip(saved);
        graphics.clip(new java.awt.geom.Rectangle2D.Float(left, line.y(), Math.max(1f, right - left),
                textLayout.lineHeight()));
        graphics.setColor(color);
        line.text().draw(graphics, TextLayoutEngine.HORIZONTAL_PADDING, line.y() + textLayout.ascent());
    }

    /** Номер физической строки и положение её первой визуальной строки (координаты компонента). */
    public record LineMark(int number, int y, int baseline) {
    }

    private final java.util.List<Runnable> viewListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Слушатель вызывается при смене документа, каретки, ширины и шрифта: колонка номеров перерисовывается. */
    public void addViewListener(Runnable listener) {
        viewListeners.add(listener);
    }

    public void removeViewListener(Runnable listener) {
        viewListeners.remove(listener);
    }

    private void fireViewChanged() {
        for (Runnable listener : viewListeners) {
            listener.run();
        }
    }

    /** Номера строк в видимой области: у перенесённой строки номер ставится только у первой визуальной. */
    public List<LineMark> visibleLineMarks(Rectangle clip) {
        if (document == null) {
            return List.of();
        }
        int top = typewriterMode.topPadding(this);
        Rectangle content = new Rectangle(clip);
        content.y -= top;
        List<LineMark> marks = new java.util.ArrayList<>();
        for (TextLayoutEngine.VisualLine line : textLayout.visibleLines(content)) {
            int logical = textLayout.logicalLineAt(line.start());
            if (line.start() == textLayout.logicalLineStart(logical)) {
                marks.add(new LineMark(logical + 1, line.y() + top, line.y() + top + textLayout.ascent()));
            }
        }
        return marks;
    }

    public int lineCount() {
        return document == null ? 1 : textLayout.logicalLineAt(document.length()) + 1;
    }

    public int currentLineNumber() {
        return document == null ? 1 : textLayout.logicalLineAt(caretOffset) + 1;
    }

    /** Выделяет строки целиком между двумя точками по вертикали (координаты компонента), как клик по номеру строки. */
    public void selectLines(int fromY, int toY) {
        if (document == null) {
            return;
        }
        int first = textLayout.logicalLineAt(hitTest(new Point(0, fromY)));
        int second = textLayout.logicalLineAt(hitTest(new Point(0, toY)));
        int start = textLayout.logicalLineStart(Math.min(first, second));
        int end = textLayout.nextLogicalLineStart(Math.max(first, second));
        if (second >= first) {
            setCaretState(end, start);
        } else {
            setCaretState(start, end);
        }
        clearComposition();
        requestFocusInWindow();
        repaint();
    }

    public int lineHeight() {
        return textLayout.lineHeight();
    }

    public int homeOffset(int offset) {
        return textLayout.homeOffset(offset);
    }

    public int endOffset(int offset) {
        return textLayout.endOffset(offset);
    }

    /** Точка расширения для переключения интерактивных элементов содержимого. */
    public boolean toggleTaskAt(Point point) {
        return false;
    }

    /** Точка расширения для навигации по ссылке содержимого. */
    public boolean openLinkAt(Point point) {
        return false;
    }

    /** Точка расширения для изменения размера визуального объекта. */
    public boolean beginImageResize(Point point) {
        return false;
    }

    public void finishImageResize(Point point) {
    }

    TextLayoutEngine textLayout() {
        return textLayout;
    }

    public int visiblePageHeight() {
        Rectangle visible = getVisibleRect();
        return visible.height > 0 ? visible.height : getHeight();
    }

    public void ensureCaretVisible() {
        if (document == null) {
            return;
        }
        if (typewriterMode.isEnabled()) {
            typewriterMode.centerCaret(this);
            return;
        }
        Rectangle caret = componentCaretRect(caretOffset);
        caret.grow(12, 4);
        scrollRectToVisible(caret);
    }

    public void clearComposition() {
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
        setNativeImageDropEnabled(false);
        onDispose();
        removeAll();
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

    protected void onDispose() {
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        textLayout.setFont(font);
        fireViewChanged();
    }

    public void importImages(List<Path> files, BufferedImage image, int offset) {
    }

    protected boolean supportsImageInput() {
        return false;
    }

    protected boolean canImportImages(Transferable transferable) {
        return transferable != null && canImportImages(transferable.getTransferDataFlavors());
    }

    protected boolean canImportImages(DataFlavor[] flavors) {
        if (document == null || !supportsImageInput() || flavors == null) {
            return false;
        }
        for (DataFlavor flavor : flavors) {
            if (DataFlavor.javaFileListFlavor.equals(flavor) || DataFlavor.imageFlavor.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    protected boolean importTransferable(Transferable transferable, int offset) {
        if (!canImportImages(transferable)) {
            return false;
        }
        try {
            List<Path> files = List.of();
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<java.io.File> dropped = (List<java.io.File>) transferable
                        .getTransferData(DataFlavor.javaFileListFlavor);
                files = dropped.stream().map(java.io.File::toPath).toList();
            }
            BufferedImage image = transferable.isDataFlavorSupported(DataFlavor.imageFlavor)
                    ? (BufferedImage) transferable.getTransferData(DataFlavor.imageFlavor) : null;
            importImages(files, image, offset);
            return true;
        } catch (Exception error) {
            setStatus("Cannot import image: " + error.getMessage());
            return false;
        }
    }

    private final class ImageDropTargetListener extends DropTargetAdapter {
        @Override
        public void dragEnter(DropTargetDragEvent event) {
            acceptDrag(event);
        }

        @Override
        public void dragOver(DropTargetDragEvent event) {
            acceptDrag(event);
        }

        private void acceptDrag(DropTargetDragEvent event) {
            if (canImportImages(event.getCurrentDataFlavors())) {
                event.acceptDrag(DnDConstants.ACTION_COPY);
            } else {
                event.rejectDrag();
            }
        }

        @Override
        public void drop(DropTargetDropEvent event) {
            if (!canImportImages(event.getTransferable())) {
                event.rejectDrop();
                return;
            }
            event.acceptDrop(DnDConstants.ACTION_COPY);
            boolean imported = importTransferable(event.getTransferable(), hitTest(event.getLocation()));
            event.dropComplete(imported);
        }
    }

    private final class ImageTransferHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return canImportImages(support.getTransferable());
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            int offset = support.isDrop()
                    ? hitTest(support.getDropLocation().getDropPoint()) : caretOffset;
            return importTransferable(support.getTransferable(), offset);
        }
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
            g.setColor(contentBackground());
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

    protected Color contentBackground() {
        return getBackground();
    }

    private void paintDocument(Graphics2D graphics) {
        syncContentComponents();
        graphics.translate(0, typewriterMode.topPadding(this));
        Rectangle clip = graphics.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        paintContent(graphics, clip);
        imeSupport.paint(graphics);
        caretPainter.paint(graphics);
    }

    protected void paintContent(Graphics2D graphics, Rectangle clip) {
        List<TextLayoutEngine.VisualLine> lines = textLayout.visibleLines(clip);
        selectionPainter.paint(graphics, lines);
        graphics.setColor(getForeground());
        for (TextLayoutEngine.VisualLine line : lines) {
            if (line.text() != null) {
                if (highlighter == null) {
                    line.text().draw(graphics, TextLayoutEngine.HORIZONTAL_PADDING,
                            line.y() + textLayout.ascent());
                } else {
                    paintHighlighted(graphics, line);
                }
            }
        }
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

    protected void syncContentComponents() {
    }

    protected int contentHeight() {
        return textLayout.preferredHeight();
    }

    @Override
    public Dimension getPreferredSize() {
        if (document == null) {
            return new Dimension(800, 600);
        }
        syncContentComponents();
        return new Dimension(800, contentHeight() + typewriterMode.topPadding(this) + typewriterMode.bottomPadding(this));
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
        onDocumentContentChanged(changes);
        inputController.documentChanged(changes.getLast());
        caretPainter.documentChanged();
        fireViewChanged();
        revalidate();
        typewriterMode.centerCaret(this);
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
