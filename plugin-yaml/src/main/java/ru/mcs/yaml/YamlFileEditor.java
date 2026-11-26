package ru.mcs.yaml;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import ru.mcs.editor.model.DocumentListener;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.DocumentModel.SavePoint;
import ru.mcs.editor.model.DocumentModelImpl;
import ru.mcs.editor.model.DocumentProperties;
import ru.mcs.editor.model.Range;
import ru.mcs.editor.model.Replace;
import ru.mcs.editor.model.TextDocumentCodec;
import ru.mcs.editor.model.TextDocumentCodec.DecodedDocument;
import ru.mcs.editor.view.CommonKeymap;
import ru.mcs.editor.view.EditorCanvas;
import ru.mcs.editor.view.LineNumberGutter;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.editor.keymap.KeymapService;

/** YAML-файл на универсальном Canvas: номера строк, подсветка, статусная строка проверки и свои команды. */
final class YamlFileEditor implements FileEditor {

    private static final Color ERROR = new Color(0xD32F2F);

    private final Path file;
    private final YamlEditorServices services;
    private final Consumer<YamlFileEditor> onDispose;
    private final EditorCanvas canvas = new EditorCanvas();
    private final JScrollPane scroll = new JScrollPane(canvas);
    private final LineNumberGutter gutter = new LineNumberGutter(canvas);
    private final JLabel status = new JLabel(" ");
    private final JPanel root = new JPanel(new BorderLayout());
    private final SwingWorker<DecodedDocument, Void> loader;
    private final List<Runnable> modifiedListeners = new CopyOnWriteArrayList<>();
    private final DocumentListener documentListener = change -> {
        updateModified();
        scheduleValidation();
    };
    private final Timer validationTimer = new Timer(350, event -> validateNow());
    private final YamlKeymap keymap;

    private volatile boolean loading = true;
    private volatile boolean disposed;
    private DocumentModel document;
    private boolean modified;
    private CompletableFuture<Void> saveInFlight;

    YamlFileEditor(Path file, YamlEditorServices services, Consumer<YamlFileEditor> onDispose) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.services = Objects.requireNonNull(services, "services");
        this.onDispose = Objects.requireNonNull(onDispose, "onDispose");
        root.setName("yaml.editor");
        status.setName("yaml.editor.status");
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        root.add(scroll, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        validationTimer.setRepeats(false);
        keymap = new YamlKeymap(canvas, Map.of(
                YamlKeymapScopes.TOGGLE_COMMENT, this::toggleComment,
                YamlKeymapScopes.REFORMAT, this::reformat,
                YamlKeymapScopes.VALIDATE, this::validateNow));
        keymap.install(null);
        canvas.setStatus(services.i18n().get("status.loading"));
        loader = createLoader();
        loader.execute();
    }

    private SwingWorker<DecodedDocument, Void> createLoader() {
        return new SwingWorker<>() {
            @Override
            protected DecodedDocument doInBackground() throws Exception {
                return TextDocumentCodec.decode(services.files().read(file));
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
                    validateNow();
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

    /** Применяет номера строк, подсветку и проверку из настроек модуля. */
    void applySettings() {
        YamlSettings settings = services.settings();
        scroll.setRowHeaderView(settings.lineNumbers() ? gutter : null);
        canvas.setSyntaxHighlighting(settings.highlighting() ? new YamlSyntaxHighlighter() : null,
                services.colors().get());
        validateNow();
    }

    /** Ставит общие клавиши и клавиши YAML с учётом настроек пользователя. */
    void useKeymap() {
        KeymapService service = services.keymap().get();
        if (service != null) {
            CommonKeymap.apply(canvas, service);
        }
        keymap.install(service);
    }

    private void scheduleValidation() {
        if (services.settings().validate()) {
            validationTimer.restart();
        }
    }

    void validateNow() {
        validationTimer.stop();
        if (document == null) {
            return;
        }
        if (!services.settings().validate()) {
            show(" ", false);
            return;
        }
        String text = document.text().toString();
        if (text.length() > YamlValidator.MAX_CHARS) {
            show(services.i18n().get("status.skipped"), false);
            return;
        }
        YamlValidator.Problem problem = YamlValidator.validate(text, services.settings().strictKeys());
        if (problem == null) {
            show(services.i18n().get("status.valid"), false);
        } else {
            show(services.i18n().get("status.line") + " " + problem.line() + ", "
                    + services.i18n().get("status.column") + " " + problem.column() + ": " + problem.message(), true);
        }
    }

    private void show(String text, boolean error) {
        status.setText(text);
        Color normal = UIManager.getColor("Label.foreground");
        status.setForeground(error ? ERROR : normal);
    }

    void toggleComment() {
        if (document == null) {
            return;
        }
        String text = document.text().toString();
        Range selection = canvas.selectionRange();
        int start = text.lastIndexOf('\n', Math.max(0, selection.start()) - 1) + 1;
        int limit = selection.length() > 0 && text.charAt(selection.end() - 1) == '\n'
                ? selection.end() - 1 : selection.end();
        int end = text.indexOf('\n', limit);
        end = end < 0 ? text.length() : end;
        String block = text.substring(start, end);
        String toggled = CommentToggler.toggle(block);
        if (toggled.equals(block)) {
            return;
        }
        int caret = canvas.caretOffset();
        document.apply(Replace.of(start, end, toggled));
        if (selection.length() > 0) {
            canvas.setCaretSelection(start, start + toggled.length());
        } else {
            int restored = Math.max(start, Math.min(caret + toggled.length() - block.length(), document.length()));
            canvas.setCaretSelection(restored, restored);
        }
    }

    void reformat() {
        if (document == null) {
            return;
        }
        String text = document.text().toString();
        String formatted = YamlFormatter.format(text, services.codeStyle().style());
        if (formatted.equals(text)) {
            return;
        }
        int caret = canvas.caretOffset();
        document.apply(Replace.of(0, text.length(), formatted));
        int restored = Math.min(caret, document.length());
        canvas.setCaretSelection(restored, restored);
    }

    boolean isLoaded() {
        return !loading;
    }

    String statusText() {
        return status.getText();
    }

    EditorCanvas canvas() {
        return canvas;
    }

    DocumentModel document() {
        return document;
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
        services.files().write(file, TextDocumentCodec.encode(snapshot.text(), snapshot.properties()));
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
        validationTimer.stop();
        loader.cancel(true);
        if (document != null) {
            document.removeListener(documentListener);
        }
        keymap.uninstall();
        modifiedListeners.clear();
        gutter.dispose();
        canvas.dispose();
        root.removeAll();
        onDispose.accept(this);
    }
}
