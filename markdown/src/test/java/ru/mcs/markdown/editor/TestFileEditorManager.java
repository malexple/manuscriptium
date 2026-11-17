package ru.mcs.markdown.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.editor.FileEditorManager;
import ru.mcs.spi.swing.editor.FileEditorProvider;

/** Минимальная модель вкладок без зависимости тестов Markdown от core. */
final class TestFileEditorManager implements FileEditorManager {

    private final List<FileEditorProvider> providers = new ArrayList<>();
    private final Map<Path, FileEditor> editors = new LinkedHashMap<>();
    private Path selected;

    @Override
    public void registerProvider(FileEditorProvider provider) {
        providers.add(provider);
    }

    @Override
    public void unregisterProvider(FileEditorProvider provider) {
        providers.remove(provider);
    }

    @Override
    public boolean openFile(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        FileEditor existing = editors.get(normalized);
        if (existing == null) {
            FileEditorProvider provider = providers.stream().filter(candidate -> candidate.accept(normalized))
                    .max(Comparator.comparingInt(FileEditorProvider::getPriority)).orElse(null);
            if (provider == null) {
                return false;
            }
            try {
                existing = provider.createEditor(normalized);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            editors.put(normalized, existing);
        }
        selected = normalized;
        return true;
    }

    @Override
    public boolean closeFile(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        FileEditor removed = editors.remove(normalized);
        if (removed != null) {
            removed.dispose();
        }
        if (normalized.equals(selected)) {
            selected = editors.keySet().stream().findFirst().orElse(null);
        }
        return true;
    }

    @Override
    public List<Path> getOpenFiles() {
        return List.copyOf(editors.keySet());
    }

    @Override
    public Optional<Path> getSelectedFile() {
        return Optional.ofNullable(selected);
    }

    @Override
    public Optional<FileEditor> selectedEditor() {
        return Optional.ofNullable(editors.get(selected));
    }

    @Override
    public void selectFile(Path file) {
        selected = file.toAbsolutePath().normalize();
    }

    @Override
    public boolean isPinned(Path file) {
        return false;
    }

    @Override
    public void setPinned(Path file, boolean pinned) {
    }

    @Override
    public void saveAll() throws IOException {
        for (FileEditor editor : editors.values()) {
            editor.save();
        }
    }
}
