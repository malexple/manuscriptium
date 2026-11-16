package ru.mcs.core.project;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.service.AtomicFileStore;

/** Виртуальная книжная вкладка: редакторы глав идут подряд, а сохранение адресует исходные файлы. */
final class BookViewEditor implements FileEditor {

    private final Path key;
    private final String name;
    private final Path root;
    private final ViewDefinition view;
    private final List<Integer> nodePath;
    private final AtomicFileStore store;
    private final JPanel component = new JPanel(new BorderLayout());
    private final JPanel chapters = new JPanel();
    private final JLabel status = new JLabel(Messages.get("book.loading"));
    private final JButton save = new JButton(Messages.get("book.save"));
    private final JButton export = new JButton(Messages.get("book.export"));
    private final Map<Path, String> original = new HashMap<>();
    private final Map<Path, String> current = new LinkedHashMap<>();
    private final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private BookViewDocument document;
    private boolean modified;
    private boolean saving;
    private boolean disposed;

    BookViewEditor(Path key, String name, Path root, ViewDefinition view, List<Integer> nodePath,
                   AtomicFileStore store) {
        this.key = key.toAbsolutePath().normalize();
        this.name = name;
        this.root = root.toAbsolutePath().normalize();
        this.view = view;
        this.nodePath = List.copyOf(nodePath);
        this.store = store;
        chapters.setLayout(new BoxLayout(chapters, BoxLayout.Y_AXIS));
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(save);
        toolbar.add(export);
        toolbar.add(status);
        save.setEnabled(false);
        export.setEnabled(false);
        save.addActionListener(event -> saveAsync());
        export.addActionListener(event -> export());
        component.add(toolbar, BorderLayout.NORTH);
        component.add(new JScrollPane(chapters), BorderLayout.CENTER);
        load();
    }

    @Override
    public Path getFile() {
        return key;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JComponent getComponent() {
        return component;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void save() throws IOException {
        Map<Path, String> changed = changed();
        if (changed.isEmpty()) {
            return;
        }
        persist(changed);
        original.putAll(changed);
        updateModified();
        status.setText(Messages.get("book.saved"));
    }

    @Override
    public CompletableFuture<Void> saveAsync() {
        Map<Path, String> changed = changed();
        if (changed.isEmpty() || saving || disposed) {
            return CompletableFuture.completedFuture(null);
        }
        saving = true;
        save.setEnabled(false);
        status.setText(Messages.get("book.saving"));
        CompletableFuture<Void> result = new CompletableFuture<>();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                persist(changed);
                return null;
            }

            @Override
            protected void done() {
                saving = false;
                try {
                    get();
                    original.putAll(changed);
                    status.setText(Messages.get("book.saved"));
                    result.complete(null);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    fail(error);
                } catch (ExecutionException error) {
                    fail(error.getCause());
                } finally {
                    updateModified();
                }
            }

            private void fail(Throwable error) {
                status.setText(error.getMessage() == null ? error.toString() : error.getMessage());
                result.completeExceptionally(error);
            }
        }.execute();
        return result;
    }

    @Override
    public void addModifiedListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    private void load() {
        new SwingWorker<BookViewDocument, Void>() {
            @Override
            protected BookViewDocument doInBackground() throws Exception {
                return BookViewDocument.load(root, view, nodePath, store);
            }

            @Override
            protected void done() {
                if (disposed) {
                    return;
                }
                try {
                    document = get();
                    install(document);
                    status.setText(Messages.get("book.chapters", document.chapters().size()));
                    export.setEnabled(true);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    status.setText(error.toString());
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    status.setText(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                }
            }
        }.execute();
    }

    private void install(BookViewDocument loaded) {
        chapters.removeAll();
        for (BookViewDocument.Chapter chapter : loaded.chapters()) {
            original.put(chapter.file(), chapter.source());
            current.put(chapter.file(), chapter.source());
            JTextArea editor = new JTextArea(chapter.source());
            editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);
            editor.setBorder(new EmptyBorder(12, 16, 12, 16));
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    changed(editor, chapter.file());
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    changed(editor, chapter.file());
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    changed(editor, chapter.file());
                }
            });
            JPanel section = new JPanel(new BorderLayout());
            section.setBorder(javax.swing.BorderFactory.createTitledBorder(chapter.title()));
            section.add(editor, BorderLayout.CENTER);
            section.setAlignmentX(0f);
            section.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                    Math.min(560, Math.max(140, editor.getLineCount() * 22 + 48))));
            chapters.add(section);
            chapters.add(Box.createVerticalStrut(8));
        }
        chapters.revalidate();
        chapters.repaint();
    }

    private void changed(JTextArea editor, Path file) {
        current.put(file, editor.getText());
        updateModified();
    }

    private Map<Path, String> changed() {
        Map<Path, String> result = new LinkedHashMap<>();
        current.forEach((file, text) -> {
            if (!text.equals(original.get(file))) {
                result.put(file, text);
            }
        });
        return result;
    }

    private void persist(Map<Path, String> changed) throws IOException {
        if (document == null) {
            throw new IOException("Book is not loaded");
        }
        document.save(changed, store);
    }

    private void updateModified() {
        boolean value = !changed().isEmpty();
        save.setEnabled(value && !saving);
        if (modified != value) {
            modified = value;
            listeners.forEach(Runnable::run);
        }
    }

    private void export() {
        if (document == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser(root.toFile());
        chooser.setSelectedFile(root.resolve(view.id() + ".md").toFile());
        if (chooser.showSaveDialog(component) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        Map<Path, String> snapshot = Map.copyOf(current);
        export.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                document.export(target, snapshot);
                return null;
            }

            @Override
            protected void done() {
                export.setEnabled(true);
                try {
                    get();
                    status.setText(Messages.get("book.exported", target));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    status.setText(error.toString());
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    status.setText(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                }
            }
        }.execute();
    }
}
