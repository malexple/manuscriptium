package ru.mcs.core.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import javax.swing.JPanel;
import ru.mcs.spi.editor.FileEditor;

final class FakeEditor implements FileEditor {

    final Path file;
    boolean modified;
    boolean failOnSave;
    int saves;
    boolean disposed;
    CompletableFuture<Void> pendingAsyncSave;
    private final JPanel panel = new JPanel();
    private final List<Runnable> listeners = new ArrayList<>();

    FakeEditor(Path file) {
        this.file = file;
    }

    void setModified(boolean value) {
        modified = value;
        listeners.forEach(Runnable::run);
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public String getName() {
        return file.getFileName().toString();
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void save() throws IOException {
        if (failOnSave) {
            throw new IOException("disk full");
        }
        saves++;
        setModified(false);
    }

    @Override
    public CompletableFuture<Void> saveAsync() {
        if (pendingAsyncSave == null) {
            return FileEditor.super.saveAsync();
        }
        return pendingAsyncSave.thenRun(() -> {
            saves++;
            setModified(false);
        });
    }

    @Override
    public void addModifiedListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
