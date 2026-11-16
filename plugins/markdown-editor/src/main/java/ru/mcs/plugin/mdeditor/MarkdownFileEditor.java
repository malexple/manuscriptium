package ru.mcs.plugin.mdeditor;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import ru.mcs.plugin.mdeditor.action.EditorBlockContext;
import ru.mcs.plugin.mdeditor.action.EditorContextMenuBuilder;
import ru.mcs.plugin.mdeditor.action.TyporaKeymap;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.editor.model.DocumentListener;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.DocumentModel.SavePoint;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.Insert;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.TextDocumentCodec;
import ru.mcs.editor.model.TextDocumentCodec.DecodedDocument;
import ru.mcs.editor.model.Replace;
import ru.mcs.plugin.mdeditor.view.MarkdownCanvas;
import ru.mcs.plugin.mdeditor.view.MarginNotesPane;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.swing.editor.style.EditorStyleService;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.AtomicFileStore;

/** Одна Markdown-вкладка; Canvas и редактируемый Source используют общую DocumentModel. */
final class MarkdownFileEditor implements FileEditor {

    private static final String CANVAS_CARD = "canvas";
    private static final String SOURCE_CARD = "source";

    private final Path file;
    private final MarkdownEditorRegistry registry;
    private final I18n i18n;
    private final AtomicFileStore fileStore;
    private final MarkdownParser parser;
    private final Executor parserExecutor;
    private final JPanel root = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final MarkdownCanvas canvas = new MarkdownCanvas();
    private final MarkdownCanvas source = new MarkdownCanvas();
    private final JToggleButton canvasButton;
    private final JToggleButton sourceButton;
    private final JToggleButton notesButton;
    private final MarginNotesPane marginNotes;
    private final SwingWorker<DecodedDocument, Void> loader;
    private final List<Runnable> modifiedListeners = new CopyOnWriteArrayList<>();
    private final DocumentListener documentListener = change -> documentChanged();

    private volatile boolean loading = true;
    private volatile boolean disposed;
    private EditorMode mode;
    private DocumentModel document;
    private MarkdownParseCoordinator parseCoordinator;
    private boolean modified;
    private CompletableFuture<Void> saveInFlight;

    MarkdownFileEditor(Path file, EditorMode initialMode, MarkdownEditorRegistry registry, I18n i18n,
                       AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor) {
        this(file, initialMode, registry, i18n, fileStore, parser, parserExecutor, null, null);
    }

    MarkdownFileEditor(Path file, EditorMode initialMode, MarkdownEditorRegistry registry, I18n i18n,
                       AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor,
                       EditorStyleService styles) {
        this(file, initialMode, registry, i18n, fileStore, parser, parserExecutor, styles, null);
    }

    MarkdownFileEditor(Path file, EditorMode initialMode, MarkdownEditorRegistry registry, I18n i18n,
                       AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor,
                       EditorStyleService styles, ActionManager actions) {
        this(file, initialMode, registry, i18n, fileStore, parser, parserExecutor, styles, actions, null);
    }

    MarkdownFileEditor(Path file, EditorMode initialMode, MarkdownEditorRegistry registry, I18n i18n,
                       AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor,
                       EditorStyleService styles, ActionManager actions, WidgetRegistry widgets) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.registry = registry;
        this.i18n = i18n;
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.parserExecutor = Objects.requireNonNull(parserExecutor, "parserExecutor");

        if (styles != null) {
            canvas.setEditorStyles(styles);
        }
        if (widgets != null) {
            canvas.setWidgets(widgets, this.file.getParent(), this.file);
            source.setWidgets(widgets, this.file.getParent(), this.file);
        }
        source.setName("markdown.editor.source");
        source.setStatus(i18n.get("source.loading"));
        canvas.setStatus(i18n.get("canvas.loading"));
        if (actions != null) {
            TyporaKeymap keymap = new TyporaKeymap(actions);
            keymap.install(canvas);
            keymap.install(source);
            EditorContextMenuBuilder contextMenus = new EditorContextMenuBuilder(actions);
            canvas.installContextMenu(contextMenus, this::editorContext);
            source.installContextMenu(contextMenus, this::editorContext);
        }
        content.add(new JScrollPane(canvas), CANVAS_CARD);
        content.add(new JScrollPane(source), SOURCE_CARD);
        // CardLayout делит одно native окно: активный drop-target должен быть только у видимой карточки.
        canvas.setNativeImageDropEnabled(false);
        source.setNativeImageDropEnabled(false);

        canvasButton = modeButton("markdown.editor.mode.canvas", i18n.get("mode.canvas"));
        sourceButton = modeButton("markdown.editor.mode.source", i18n.get("mode.source"));
        ButtonGroup group = new ButtonGroup();
        group.add(canvasButton);
        group.add(sourceButton);
        canvasButton.addActionListener(e -> setMode(EditorMode.WYSIWYG));
        sourceButton.addActionListener(e -> setMode(EditorMode.SOURCE));
        marginNotes = new MarginNotesPane(i18n.get("notes.empty"), new MarginNotesPane.Handler() {
            @Override
            public void select(int offset) {
                if (document != null) {
                    activeCanvas().setCaretSelection(offset, offset);
                    activeCanvas().requestFocusInWindow();
                }
            }

            @Override
            public void add(String text) {
                addMarginNote(text);
            }

            @Override
            public void edit(ru.mcs.plugin.mdeditor.view.ObsidianComments.Comment comment, String text) {
                editMarginNote(comment, text);
            }
        }, i18n);
        marginNotes.setVisible(false);
        notesButton = modeButton("markdown.editor.notes", i18n.get("mode.notes"));
        notesButton.addActionListener(event -> marginNotes.setVisible(notesButton.isSelected()));
        JToolBar modes = new JToolBar();
        modes.setName("markdown.editor.modes");
        modes.setFloatable(false);
        modes.add(canvasButton);
        modes.add(sourceButton);
        modes.addSeparator();
        modes.add(notesButton);

        root.add(modes, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(marginNotes, BorderLayout.EAST);
        registry.register(this);
        setMode(initialMode);
        loader = createLoader();
        loader.execute();
    }

    private JToggleButton modeButton(String name, String text) {
        JToggleButton button = new JToggleButton(text);
        button.setName(name);
        button.setFocusable(false);
        return button;
    }

    private SwingWorker<DecodedDocument, Void> createLoader() {
        return new SwingWorker<>() {
            @Override
            protected DecodedDocument doInBackground() throws Exception {
                return TextDocumentCodec.decode(fileStore.read(file));
            }

            @Override
            protected void done() {
                loading = false;
                if (disposed || isCancelled()) {
                    return;
                }
                try {
                    DecodedDocument loaded = get();
                    document = new DocumentModelImpl(loaded.text(), loaded.properties());
                    document.addListener(documentListener);
                    source.setDocument(document);
                    canvas.setDocument(document);
                    parseCoordinator = new MarkdownParseCoordinator(document, parser, parserExecutor,
                            index -> {
                                if (!disposed) {
                                    canvas.setAstIndex(index);
                                }
                            });
                    canvas.setStatus(i18n.get("canvas.ready"));
                    marginNotes.setComments(document.text().toString(), i18n.get("notes.empty"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showLoadError(e);
                } catch (ExecutionException | CancellationException e) {
                    showLoadError(e);
                }
            }
        };
    }

    private void showLoadError(Exception error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null
                ? error.getCause() : error;
        String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        source.setStatus(i18n.get("source.error", detail));
        canvas.setStatus(i18n.get("canvas.error", detail));
    }

    void setMode(EditorMode newMode) {
        if (disposed) {
            return;
        }
        EditorMode requested = Objects.requireNonNull(newMode, "mode");
        MarkdownCanvas previous = activeCanvas();
        mode = requested;
        boolean sourceMode = mode == EditorMode.SOURCE;
        MarkdownCanvas next = activeCanvas();
        if (document != null && previous != next) {
            var selection = previous.selectionRange();
            next.setCaretSelection(selection.start(), selection.end());
        }
        if (previous != next) {
            previous.setNativeImageDropEnabled(false);
        }
        cards.show(content, sourceMode ? SOURCE_CARD : CANVAS_CARD);
        next.setNativeImageDropEnabled(true);
        sourceButton.setSelected(sourceMode);
        canvasButton.setSelected(!sourceMode);
        next.requestFocusInWindow();
    }

    EditorMode mode() {
        return mode;
    }

    MarkdownCanvas canvas() {
        return canvas;
    }

    String sourceText() {
        return document == null ? source.getStatus() : document.text().toString();
    }

    MarkdownCanvas source() {
        return source;
    }

    MarkdownCanvas activeCanvas() {
        return mode == EditorMode.SOURCE ? source : canvas;
    }

    void setFocusMode(boolean enabled) {
        canvas.setFocusMode(enabled);
    }

    boolean focusMode() {
        return canvas.focusMode();
    }

    void setTypewriterMode(boolean enabled) {
        canvas.setTypewriterMode(enabled);
        source.setTypewriterMode(enabled);
    }

    boolean typewriterMode() {
        return activeCanvas().typewriterMode();
    }

    void setAutoPair(boolean enabled) {
        canvas.setAutoPairEnabled(enabled);
        source.setAutoPairEnabled(enabled);
    }

    boolean autoPair() {
        return activeCanvas().autoPairEnabled();
    }

    private EditorBlockContext editorContext() {
        MarkdownCanvas active = activeCanvas();
        AstIndex index = astIndex();
        if (index == null || document == null || index.version() != document.version()) {
            return new EditorBlockContext(BlockKind.PARAGRAPH, active.selectionRange().length() > 0, false, false);
        }
        int offset = active.caretOffset();
        BlockKind kind = index.blockAt(offset).map(BlockSpan::kind).orElse(BlockKind.PARAGRAPH);
        boolean link = false;
        boolean image = false;
        for (BlockSpan span : index.spansAt(offset)) {
            link |= span.kind() == BlockKind.LINK || span.kind() == BlockKind.LINK_REFERENCE
                    || span.kind() == BlockKind.AUTOLINK || span.kind() == BlockKind.WIKILINK;
            image |= span.kind() == BlockKind.IMAGE || span.kind() == BlockKind.IMAGE_REFERENCE
                    || span.kind() == BlockKind.WIKI_IMAGE || span.kind() == BlockKind.EMBED;
        }
        return new EditorBlockContext(kind, active.selectionRange().length() > 0, link, image);
    }

    void addMarginNote(String text) {
        if (document == null) {
            return;
        }
        int offset = activeCanvas().caretOffset();
        document.apply(new Insert(offset, "%%" + text + "%%"));
        int caret = offset + text.length() + 4;
        activeCanvas().setCaretSelection(caret, caret);
    }

    void editMarginNote(ru.mcs.plugin.mdeditor.view.ObsidianComments.Comment comment, String text) {
        if (document != null) {
            document.apply(Replace.of(comment.start(), comment.end(), "%%" + text + "%%"));
        }
    }

    DocumentModel document() {
        return document;
    }

    AstIndex astIndex() {
        return parseCoordinator == null ? null : parseCoordinator.index();
    }

    boolean isLoaded() {
        return !loading;
    }

    private void documentChanged() {
        updateModified();
        if (document != null) {
            marginNotes.setComments(document.text().toString(), i18n.get("notes.empty"));
        }
    }

    MarginNotesPane marginNotes() {
        return marginNotes;
    }

    private void updateModified() {
        boolean value = document != null && document.isModified();
        if (modified != value) {
            modified = value;
            for (Runnable listener : modifiedListeners) {
                listener.run();
            }
        }
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public String getName() {
        Path name = file.getFileName();
        return name == null ? file.toString() : name.toString();
    }

    @Override
    public JComponent getComponent() {
        return root;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void save() throws IOException {
        SaveSnapshot snapshot = saveSnapshot();
        if (snapshot == null) {
            return;
        }
        write(snapshot);
        commitSave(snapshot);
    }

    @Override
    public CompletableFuture<Void> saveAsync() {
        if (saveInFlight != null && !saveInFlight.isDone()) {
            return saveInFlight;
        }
        SaveSnapshot snapshot = saveSnapshot();
        if (snapshot == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        saveInFlight = result;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                write(snapshot);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (!disposed) {
                        commitSave(snapshot);
                    }
                    result.complete(null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.completeExceptionally(e);
                } catch (ExecutionException | CancellationException e) {
                    Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                    result.completeExceptionally(cause);
                } finally {
                    if (saveInFlight == result) {
                        saveInFlight = null;
                    }
                }
            }
        }.execute();
        return result;
    }

    private SaveSnapshot saveSnapshot() {
        DocumentModel current = document;
        if (current == null || !current.isModified()) {
            return null;
        }
        return new SaveSnapshot(current, current.text().toString(), current.properties(), current.savePoint());
    }

    private void write(SaveSnapshot snapshot) throws IOException {
        if (snapshot.properties().mixedLineEndings()) {
            throw new IOException(i18n.get("save.error.mixedEol"));
        }
        fileStore.write(file, TextDocumentCodec.encode(snapshot.text(), snapshot.properties()));
    }

    private void commitSave(SaveSnapshot snapshot) {
        if (document == snapshot.document()) {
            document.markSaved(snapshot.savePoint());
            updateModified();
        }
    }

    private record SaveSnapshot(DocumentModel document, String text, DocumentProperties properties,
                                SavePoint savePoint) {
    }

    @Override
    public void addModifiedListener(Runnable listener) {
        modifiedListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        loader.cancel(true);
        if (parseCoordinator != null) {
            parseCoordinator.close();
        }
        if (document != null) {
            document.removeListener(documentListener);
        }
        modifiedListeners.clear();
        source.dispose();
        canvas.dispose();
        registry.remove(this);
        root.removeAll();
    }
}
