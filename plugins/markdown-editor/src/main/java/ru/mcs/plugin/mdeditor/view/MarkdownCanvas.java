package ru.mcs.plugin.mdeditor.view;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.Insert;
import ru.mcs.editor.model.Replace;
import ru.mcs.editor.view.ClipboardService;
import ru.mcs.editor.view.EditorCanvas;
import ru.mcs.plugin.mdeditor.action.EditorBlockContext;
import ru.mcs.plugin.mdeditor.action.EditorContextMenuBuilder;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.reference.CitationIndex;
import ru.mcs.plugin.mdeditor.render.MarkdownRenderer;
import ru.mcs.plugin.mdeditor.widget.ImageWidget;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.swing.editor.style.EditorStyleService;

/** Markdown-расширение универсального Canvas: styled layout, widgets, links, tables и citations. */
public final class MarkdownCanvas extends EditorCanvas {

    private final MarkdownLayoutEngine markdownLayout = new MarkdownLayoutEngine();
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer(markdownLayout);
    private final Runnable styleListener = this::editorStylesChanged;
    private final Runnable widgetListener = this::widgetsChanged;

    private AstIndex ast;
    private EditorStyleService styles;
    private WidgetRegistry widgets;
    private Path baseDirectory;
    private Path noteFile;
    private ImageInputService imageInput;
    private ImageResize imageResize;
    private JPopupMenu citationPopup;
    private EditorContextMenuBuilder contextMenuBuilder;
    private Supplier<EditorBlockContext> editorContext;

    public MarkdownCanvas() {
        super();
        setName("markdown.editor.canvas");
    }

    MarkdownCanvas(ClipboardService clipboardService) {
        super(clipboardService);
        setName("markdown.editor.canvas");
    }

    @Override
    protected void onDocumentContextChanged() {
        markdownLayout.setContext(document(), ast, styles);
        if (document() != null) {
            markdownLayout.setWidth(getWidth());
        } else {
            markdownLayout.clear();
        }
    }

    @Override
    protected void onViewportWidthChanged(int width) {
        markdownLayout.setWidth(width);
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
        markdownLayout.setContext(document(), ast, styles);
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
        markdownLayout.setContext(document(), ast, styles);
        revalidate();
        repaint();
    }

    public void setWidgets(WidgetRegistry widgets, Path baseDirectory) {
        setWidgets(widgets, baseDirectory, null);
    }

    public void setWidgets(WidgetRegistry widgets, Path baseDirectory, Path noteFile) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Editor widgets must be attached on EDT");
        }
        if (this.widgets != null) {
            this.widgets.removeChangeListener(widgetListener);
        }
        this.widgets = widgets;
        this.baseDirectory = baseDirectory;
        this.noteFile = noteFile;
        imageInput = widgets == null ? null : new ImageInputService(widgets.executor());
        if (widgets != null) {
            widgets.addChangeListener(widgetListener);
        }
        markdownLayout.setWidgets(widgets, baseDirectory, noteFile);
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

    @Override
    protected void afterTextInserted() {
        SwingUtilities.invokeLater(this::updateCitationCompletion);
    }

    private void updateCitationCompletion() {
        if (citationPopup != null) {
            citationPopup.setVisible(false);
        }
        citationPopup = createCitationCompletion();
        if (citationPopup != null && isShowing()) {
            Rectangle caret = markdownLayout.caretRect(caretOffset());
            citationPopup.show(this, caret.x, caret.y + caret.height);
        }
    }

    JPopupMenu createCitationCompletion() {
        DocumentModel document = document();
        if (disposed || document == null || widgets == null || caretOffset() != selectionAnchor()) {
            return null;
        }
        String text = document.text().toString();
        int at = text.lastIndexOf("@", Math.max(0, caretOffset() - 1));
        if (at < 1 || text.charAt(at - 1) != '[' || at >= caretOffset()) {
            return null;
        }
        String prefix = text.substring(at + 1, caretOffset());
        if (prefix.contains("]") || prefix.contains("\n") || prefix.contains("\r") || prefix.contains(";")) {
            return null;
        }
        CitationIndex citations = widgets.citations(text, noteFile);
        List<String> matches = citations.complete(prefix);
        if (matches.isEmpty()) {
            return null;
        }
        JPopupMenu popup = new JPopupMenu();
        for (String key : matches.stream().limit(12).toList()) {
            JMenuItem item = new JMenuItem(key);
            item.addActionListener(event -> {
                if (document() != null && caretOffset() >= at
                        && text.regionMatches(at, "@" + prefix, 0, prefix.length() + 1)) {
                    document().apply(Replace.of(at, caretOffset(), "@" + key));
                    setCaretState(at + key.length() + 1, at + key.length() + 1);
                }
                popup.setVisible(false);
                requestFocusInWindow();
            });
            popup.add(item);
        }
        return popup;
    }

    void insertImageMarkdown(long version, int offset, String markdown) {
        DocumentModel document = document();
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

    @Override
    public void importImages(List<Path> files, BufferedImage image, int offset) {
        if (imageInput != null && document() != null) {
            imageInput.insert(this, baseDirectory, offset, files, image);
        }
    }

    @Override
    protected boolean supportsImageInput() {
        return imageInput != null;
    }

    @Override
    public boolean beginImageResize(Point point) {
        if (!markdownMode()) {
            return false;
        }
        Point content = contentPoint(point);
        for (var block : markdownLayout.layout().blocks()) {
            if (block.widget() instanceof ImageWidget image && image.resizeHandleAt(content)) {
                imageResize = new ImageResize(image, content, document().version());
                return true;
            }
        }
        return false;
    }

    @Override
    public void finishImageResize(Point point) {
        if (imageResize == null) {
            return;
        }
        ImageResize resize = imageResize;
        imageResize = null;
        DocumentModel document = document();
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

    @Override
    public boolean navigateTable(int direction) {
        return TableController.navigate(this, direction);
    }

    @Override
    public boolean addTableRow() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.addRow(location.row()));
    }

    @Override
    public boolean removeTableRow() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.removeRow(location.row()));
    }

    @Override
    public boolean addTableColumn() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.addColumn(location.column()));
    }

    @Override
    public boolean removeTableColumn() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.removeColumn(location.column()));
    }

    public boolean alignTableColumn(ru.mcs.plugin.mdeditor.widget.TableModel.Alignment alignment) {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this, table -> table.align(location.column(), alignment));
    }

    @Override
    public boolean clearTableCell() {
        TableController.Location location = TableController.location(this);
        return location != null && TableController.edit(this,
                table -> table.setCell(location.row(), location.column(), ""));
    }

    @Override
    public String tableCellAtCaret() {
        return TableController.cell(this);
    }

    public long markdownLayoutCount() {
        return markdownLayout.layoutCount();
    }

    private boolean markdownMode() {
        return document() != null && styles != null;
    }

    @Override
    public void setFocusMode(boolean enabled) {
        markdownRenderer.setFocusMode(enabled);
        repaint();
    }

    @Override
    public boolean focusMode() {
        return markdownRenderer.focusMode();
    }

    public void installContextMenu(EditorContextMenuBuilder builder, Supplier<EditorBlockContext> context) {
        contextMenuBuilder = builder;
        editorContext = context;
    }

    @Override
    public void showContextMenu(MouseEvent event) {
        if (contextMenuBuilder == null || editorContext == null || document() == null) {
            return;
        }
        requestFocusInWindow();
        int offset = hitTest(event.getPoint());
        var selection = selectionRange();
        if (offset < selection.start() || offset >= selection.end()) {
            setCaretState(offset, offset);
            repaint();
        }
        contextMenuBuilder.build(editorContext.get()).show(this, event.getX(), event.getY());
        event.consume();
    }

    @Override
    protected void onCaretChanged(int offset) {
        markdownLayout.setCaretOffset(offset);
    }

    @Override
    public int contentHitTest(Point point) {
        return document() == null ? 0 : markdownMode() ? markdownLayout.hitTest(point) : super.contentHitTest(point);
    }

    @Override
    public Rectangle caretRect(int offset) {
        return document() == null ? new Rectangle() : markdownMode()
                ? markdownLayout.caretRect(offset) : super.caretRect(offset);
    }

    @Override
    public Color caretColor() {
        return markdownMode() ? markdownLayout.foreground() : super.caretColor();
    }

    @Override
    public int lineHeight() {
        return markdownMode() ? markdownLayout.lineHeightAt(caretOffset()) : super.lineHeight();
    }

    @Override
    public int homeOffset(int offset) {
        return markdownMode() ? markdownLayout.homeOffset(offset) : super.homeOffset(offset);
    }

    @Override
    public int endOffset(int offset) {
        return markdownMode() ? markdownLayout.endOffset(offset) : super.endOffset(offset);
    }

    @Override
    public boolean toggleTaskAt(Point point) {
        DocumentModel document = document();
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

    @Override
    public boolean openLinkAt(Point point) {
        if (!markdownMode()) {
            return false;
        }
        String target = markdownLayout.linkAt(contentPoint(point));
        if (target == null) {
            return false;
        }
        if (!(target.startsWith("https://") || target.startsWith("http://"))) {
            if (widgets == null || noteFile == null) {
                return false;
            }
            widgets.openLink(noteFile, target, this);
            return true;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
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
            System.getLogger(MarkdownCanvas.class.getName()).log(System.Logger.Level.WARNING,
                    "Cannot open link " + uri, e);
        }
    }

    @Override
    protected void onDispose() {
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
        noteFile = null;
        markdownLayout.clear();
        ast = null;
    }

    @Override
    protected Color contentBackground() {
        return markdownMode() ? markdownLayout.background() : super.contentBackground();
    }

    @Override
    protected void paintContent(Graphics2D graphics, Rectangle clip) {
        if (markdownMode()) {
            markdownRenderer.paint(graphics, clip, selectionRange());
            return;
        }
        super.paintContent(graphics, clip);
    }

    @Override
    protected void syncContentComponents() {
        if (!markdownMode()) {
            if (getComponentCount() > 0) {
                removeAll();
            }
            return;
        }
        List<JComponent> desired = new ArrayList<>();
        int padding = typewriterMode.topPadding(this);
        for (var block : markdownLayout.layout().blocks()) {
            if (block.widget() == null || block.widget().component() == null) {
                continue;
            }
            JComponent component = block.widget().component();
            component.setBounds(block.contentX(), block.contentY() + padding, block.contentWidth(),
                    block.widget().height(block.contentWidth()));
            if (component.getParent() != this) {
                add(component);
            }
            desired.add(component);
        }
        for (java.awt.Component child : getComponents()) {
            if (!desired.contains(child)) {
                remove(child);
            }
        }
    }

    @Override
    protected int contentHeight() {
        return markdownMode() ? markdownLayout.layout().height() : super.contentHeight();
    }

    @Override
    protected void onDocumentContentChanged(List<ru.mcs.editor.model.DocumentChange> changes) {
        if (markdownMode()) {
            ast = null;
            markdownLayout.setAst(null);
        }
    }

    private record ImageResize(ImageWidget widget, Point pressed, long version) {
    }
}
