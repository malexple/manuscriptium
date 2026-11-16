package ru.mcs.editor.text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import ru.mcs.editor.model.DocumentListener;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.DocumentModel.SavePoint;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.Replace;
import ru.mcs.editor.model.TextDocumentCodec;
import ru.mcs.editor.model.TextDocumentCodec.DecodedDocument;
import ru.mcs.editor.view.EditorCanvas;
import ru.mcs.spi.swing.editor.FileEditor;

/** Обычный UTF-8 файл на универсальном Canvas; загрузка и запись не блокируют EDT. */
public final class TextFileEditor implements FileEditor {

    private final Path file;
    private final TextFileStore fileStore;
    private final EditorCanvas canvas = new EditorCanvas();
    private final JScrollPane scroll = new JScrollPane(canvas);
    private final SwingWorker<DecodedDocument, Void> loader;
    private final List<Runnable> modifiedListeners = new CopyOnWriteArrayList<>();
    private final DocumentListener documentListener = change -> updateModified();

    private volatile boolean loading = true;
    private volatile boolean disposed;
    private DocumentModel document;
    private boolean modified;
    private CompletableFuture<Void> saveInFlight;

    TextFileEditor(Path file, TextFileStore fileStore) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        scroll.setName("editor.text.scroll");
        canvas.setStatus("Loading…");
        loader = createLoader();
        loader.execute();
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
                    canvas.setDocument(document);
                    canvas.requestFocusInWindow();
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
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        canvas.setStatus(cause.getMessage() == null ? cause.toString() : cause.getMessage());
    }

    boolean isLoaded() {
        return !loading;
    }

    EditorCanvas canvas() {
        return canvas;
    }

    DocumentModel document() {
        return document;
    }

    String text() {
        return document == null ? "" : document.text().toString();
    }

    void setText(String text) {
        DocumentModel current = Objects.requireNonNull(document, "document is not loaded");
        current.apply(Replace.of(0, current.length(), text));
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
        return scroll;
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

    private void updateModified() {
        boolean value = document != null && document.isModified();
        if (modified != value) {
            modified = value;
            modifiedListeners.forEach(Runnable::run);
        }
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
        if (document != null) {
            document.removeListener(documentListener);
        }
        modifiedListeners.clear();
        canvas.dispose();
        scroll.removeAll();
    }
}
