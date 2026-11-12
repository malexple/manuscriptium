package ru.mcs.plugin.mdeditor;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.plugin.mdeditor.view.EditorCanvas;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.i18n.I18n;

/** Одна Markdown-вкладка. E0: Source доступен только для чтения, Canvas — каркас без редактирования. */
final class MarkdownFileEditor implements FileEditor {

    private static final String CANVAS_CARD = "canvas";
    private static final String SOURCE_CARD = "source";

    private final Path file;
    private final MarkdownEditorRegistry registry;
    private final I18n i18n;
    private final JPanel root = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final EditorCanvas canvas = new EditorCanvas();
    private final JTextArea source = new JTextArea();
    private final JToggleButton canvasButton;
    private final JToggleButton sourceButton;
    private final SwingWorker<String, Void> loader;

    private volatile boolean loading = true;
    private volatile boolean disposed;
    private EditorMode mode;

    MarkdownFileEditor(Path file, EditorMode initialMode, MarkdownEditorRegistry registry, I18n i18n) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.registry = registry;
        this.i18n = i18n;

        source.setName("markdown.editor.source");
        source.setEditable(false);
        source.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        source.setText(i18n.get("source.loading"));
        canvas.setStatus(i18n.get("canvas.loading"));
        content.add(canvas, CANVAS_CARD);
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

    private SwingWorker<String, Void> createLoader() {
        return new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                byte[] bytes = Files.readAllBytes(file);
                try {
                    return StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
                } catch (CharacterCodingException e) {
                    throw new IllegalArgumentException("File is not valid UTF-8", e);
                }
            }

            @Override
            protected void done() {
                loading = false;
                if (disposed || isCancelled()) {
                    return;
                }
                try {
                    source.setText(get());
                    source.setCaretPosition(0);
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
        source.setText(i18n.get("source.error", detail));
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
        return source.getText();
    }

    boolean isLoaded() {
        return !loading;
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
        return false;
    }

    @Override
    public void save() {
        // E0 не изменяет документ; редактирование и атомарное сохранение появляются вместе с TextBuffer в E1.
    }

    @Override
    public void addModifiedListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        loader.cancel(true);
        registry.remove(this);
        root.removeAll();
    }
}
