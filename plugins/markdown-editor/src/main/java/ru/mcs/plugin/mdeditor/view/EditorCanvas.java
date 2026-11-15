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
import java.awt.event.MouseEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import ru.mcs.plugin.mdeditor.action.EditorContext;
import ru.mcs.plugin.mdeditor.action.EditorContextMenuBuilder;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.mode.FocusMode;
import ru.mcs.plugin.mdeditor.mode.TypewriterMode;
import ru.mcs.plugin.mdeditor.model.DocumentChange;
import ru.mcs.plugin.mdeditor.model.DocumentListener;
import ru.mcs.plugin.mdeditor.model.DocumentModel;
import ru.mcs.plugin.mdeditor.model.Range;
import ru.mcs.plugin.mdeditor.model.Insert;
import ru.mcs.plugin.mdeditor.model.Replace;
import ru.mcs.plugin.mdeditor.widget.ImageWidget;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
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
    private final FocusMode focusMode = new FocusMode();
    private final TypewriterMode typewriterMode = new TypewriterMode();
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
    private final Runnable widgetListener = this::widgetsChanged;

    private String status = "";
    private DocumentModel document;
    private AstIndex ast;
    private EditorStyleService styles;
    private WidgetRegistry widgets;
    private Path baseDirectory;
    private ImageInputService imageInput;
    private ImageResize imageResize;
    private EditorContextMenuBuilder contextMenuBuilder;
    private Supplier<EditorContext> editorContext;
    private boolean disposed;
    private boolean nativeImageDropEnabled;
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
                typewriterMode.centerCaret(EditorCanvas.this);
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
        typewriterMode.centerCaret(this);
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

    public void setWidgets(WidgetRegistry widgets, Path baseDirectory) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Editor widgets must be attached on EDT");
        }
        if (this.widgets != null) {
            this.widgets.removeChangeListener(widgetListener);
        }
        this.widgets = widgets;
        this.baseDirectory = baseDirectory;
        imageInput = widgets == null ? null : new ImageInputService(widgets.executor());
        if (widgets != null) {
            widgets.addChangeListener(widgetListener);
        }
        markdownLayout.setWidgets(widgets, baseDirectory);
        revalidate();
        repaint();
    }

    private void widgetsChanged() {
        if (SwingUtilities.isEventDispatchThread()) {
            refreshWidgets();
        } else {
            SwingUtilities.invokeLater(this::refreshWidgets);
        }
    }

    private void refreshWidgets() {
        if (disposed || widgets == null) {
            return;
        }
        markdownLayout.invalidate();
        revalidate();
        repaint();
    }

    void insertImageMarkdown(long version, int offset, String markdown) {
        if (disposed || document == null || document.version() != version) {
            return;
        }
        int target = Math.max(0, Math.min(offset, document.length()));
        String prefix = target > 0 && document.text().charAt(target - 1) != '\n' ? "\n" : "";
        String suffix = target < document.length() && document.text().charAt(target) != '\n' ? "\n" : "";
        String insertion = prefix + markdown + suffix;
        document.apply(new Insert(target, insertion));
        setCaretState(target + insertion.length(), target + insertion.length());
        ensureCaretVisible();
        repaint();
    }

    void importImages(List<Path> files, BufferedImage image, int offset) {
        if (imageInput != null && document != null) {
            imageInput.insert(this, baseDirectory, offset, files, image);
        }
    }

    /**
     * Активирует native DnD только для видимой карточки редактора. Два lightweight drop-target
     * в CardLayout используют одно heavyweight окно; их одновременная регистрация снимает XdndAware.
     */
    public void setNativeImageDropEnabled(boolean enabled) {
        if (nativeImageDropEnabled == enabled) {
            return;
        }
        nativeImageDropEnabled = enabled;
        setDropTarget(enabled
                ? new DropTarget(this, DnDConstants.ACTION_COPY, new ImageDropTargetListener(), true) : null);
    }

    boolean beginImageResize(Point point) {
        if (!markdownMode()) {
            return false;
        }
        Point content = contentPoint(point);
        for (var block : markdownLayout.layout().blocks()) {
            if (block.widget() instanceof ImageWidget image && image.resizeHandleAt(content)) {
                imageResize = new ImageResize(image, content, document.version());
                return true;
            }
        }
        return false;
    }

    void finishImageResize(Point point) {
        if (imageResize == null) {
            return;
        }
        ImageResize resize = imageResize;
        imageResize = null;
        if (document == null || document.version() != resize.version) {
            return;
        }
        int width = resize.widget.resizeWidth(contentPoint(point), resize.pressed);
        String replacement = resize.widget.resizedSource(width);
        document.apply(Replace.of(resize.widget.sourceStart(), resize.widget.sourceEnd(), replacement));
        int caret = Math.min(document.length(), resize.widget.sourceStart() + replacement.length());
        setCaretState(caret, caret);
        repaint();
    }

    public void setAstIndex(AstIndex ast) {
        this.ast = ast;
        markdownLayout.setAst(ast);
        revalidate();
        typewriterMode.centerCaret(this);
        repaint();
    }

    AstIndex astIndex() {
        return ast;
    }

    public boolean navigateTable(int direction) {
        return TableController.navigate(this, direction);
    }

    public boolean addTableRow() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.addRow(location.row()));
    }

    public boolean removeTableRow() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.removeRow(location.row()));
    }

    public boolean addTableColumn() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.addColumn(location.column()));
    }

    public boolean removeTableColumn() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.removeColumn(location.column()));
    }

    public boolean alignTableColumn(ru.mcs.plugin.mdeditor.widget.TableModel.Alignment alignment) {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.align(location.column(), alignment));
    }

    public boolean clearTableCell() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this,
                table -> table.setCell(location.row(), location.column(), ""));
    }

    public String tableCellAtCaret() {
        return TableController.cell(this);
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

    public void setFocusMode(boolean enabled) {
        focusMode.setEnabled(enabled);
        markdownLayout.setFocusMode(enabled);
        repaint();
    }

    public boolean focusMode() {
        return focusMode.isEnabled();
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

    public void installContextMenu(EditorContextMenuBuilder builder, Supplier<EditorContext> context) {
        contextMenuBuilder = builder;
        editorContext = context;
    }

    void showContextMenu(MouseEvent event) {
        if (contextMenuBuilder == null || editorContext == null || document == null) {
            return;
        }
        requestFocusInWindow();
        int offset = hitTest(event.getPoint());
        Range selection = selectionRange();
        if (offset < selection.start() || offset >= selection.end()) {
            setCaretState(offset, offset);
            repaint();
        }
        contextMenuBuilder.build(editorContext.get()).show(this, event.getX(), event.getY());
        event.consume();
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

    public void setCaretSelection(int anchor, int caret) {
        setCaretState(caret, anchor);
        clearComposition();
        ensureCaretVisible();
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
        typewriterMode.centerCaret(this);
    }

    public int hitTest(Point point) {
        return contentHitTest(contentPoint(point));
    }

    int contentHitTest(Point point) {
        return document == null ? 0 : markdownMode() ? markdownLayout.hitTest(point) : textLayout.hitTest(point);
    }

    private Point contentPoint(Point point) {
        return new Point(point.x, point.y - typewriterMode.topPadding(this));
    }

    public Rectangle caretRect(int offset) {
        return document == null ? new Rectangle() : markdownMode()
                ? markdownLayout.caretRect(offset) : textLayout.caretRect(offset);
    }

    Rectangle componentCaretRect(int offset) {
        Rectangle caret = caretRect(offset);
        caret.y += typewriterMode.topPadding(this);
        return caret;
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
        int offset = markdownLayout.taskCheckboxAt(contentPoint(point));
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
        String target = markdownLayout.linkAt(contentPoint(point));
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
        if (typewriterMode.isEnabled()) {
            typewriterMode.centerCaret(this);
            return;
        }
        Rectangle caret = componentCaretRect(caretOffset);
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
        setNativeImageDropEnabled(false);
        if (styles != null) {
            styles.removeChangeListener(styleListener);
            styles = null;
        }
        if (widgets != null) {
            widgets.removeChangeListener(widgetListener);
            widgets = null;
        }
        imageResize = null;
        imageInput = null;
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

    private record ImageResize(ImageWidget widget, Point pressed, long version) {
    }

    private boolean canImportImages(Transferable transferable) {
        return transferable != null && canImportImages(transferable.getTransferDataFlavors());
    }

    private boolean canImportImages(DataFlavor[] flavors) {
        if (document == null || imageInput == null || flavors == null) {
            return false;
        }
        for (DataFlavor flavor : flavors) {
            if (DataFlavor.javaFileListFlavor.equals(flavor) || DataFlavor.imageFlavor.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    private boolean importTransferable(Transferable transferable, int offset) {
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
            if (!canImportImages(event.getCurrentDataFlavors())) {
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
        graphics.translate(0, typewriterMode.topPadding(this));
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
        int contentHeight = markdownMode() ? markdownLayout.layout().height() : textLayout.preferredHeight();
        return new Dimension(800, contentHeight + typewriterMode.topPadding(this) + typewriterMode.bottomPadding(this));
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
