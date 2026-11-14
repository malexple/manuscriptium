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
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.plugin.mdeditor.model.DocumentListener;
import ru.mcs.plugin.mdeditor.model.DocumentModel;
import ru.mcs.plugin.mdeditor.model.DocumentModel.SavePoint;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.MarkdownDocumentCodec;
import ru.mcs.plugin.mdeditor.model.MarkdownDocumentCodec.DecodedDocument;
import ru.mcs.plugin.mdeditor.view.EditorCanvas;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.MarkdownParser;
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
    private final EditorCanvas canvas = new EditorCanvas();
    private final EditorCanvas source = new EditorCanvas();
    private final JToggleButton canvasButton;
    private final JToggleButton sourceButton;
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
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.registry = registry;
        this.i18n = i18n;
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.parserExecutor = Objects.requireNonNull(parserExecutor, "parserExecutor");

        source.setName("markdown.editor.source");
        source.setStatus(i18n.get("source.loading"));
        canvas.setStatus(i18n.get("canvas.loading"));
        content.add(new JScrollPane(canvas), CANVAS_CARD);
        content.add(new JScrollPane(source), SOURCE_CARD);

        canvasButton = modeButton("markdown.editor.mode.canvas", i18n.get("mode.canvas"));
        sourceButton = modeButton("markdown.editor.mode.source", i18n.get("mode.source"));
        ButtonGroup group = new ButtonGroup();
        group.add(canvasButton);
        group.add(sourceButton);
        canvasButton.addActionListener(e -> setMode(EditorMode.WYSIWYG));
        sourceButton.addActionListener(e -> setMode(EditorMode.SOURCE));
        JToolBar modes = new JToolBar();
        modes.setName("markdown.editor.modes");
        modes.setFloatable(false);
        modes.add(canvasButton);
        modes.add(sourceButton);

        root.add(modes, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
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
                return MarkdownDocumentCodec.decode(fileStore.read(file));
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
                    parseCoordinator = new MarkdownParseCoordinator(document, parser, parserExecutor);
                    canvas.setStatus(i18n.get("canvas.ready"));
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
        mode = Objects.requireNonNull(newMode, "mode");
        boolean sourceMode = mode == EditorMode.SOURCE;
        cards.show(content, sourceMode ? SOURCE_CARD : CANVAS_CARD);
        sourceButton.setSelected(sourceMode);
        canvasButton.setSelected(!sourceMode);
        if (sourceMode) {
            source.requestFocusInWindow();
        } else {
            canvas.requestFocusInWindow();
        }
    }

    EditorMode mode() {
        return mode;
    }

    EditorCanvas canvas() {
        return canvas;
    }

    String sourceText() {
        return document == null ? source.getStatus() : document.text().toString();
    }

    EditorCanvas source() {
        return source;
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
        fileStore.write(file, MarkdownDocumentCodec.encode(snapshot.text(), snapshot.properties()));
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
