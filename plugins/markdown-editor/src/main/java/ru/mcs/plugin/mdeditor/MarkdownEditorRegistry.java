package ru.mcs.plugin.mdeditor;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Связывает действие плагина с редактором выбранной вкладки; записи удаляются при dispose. */
final class MarkdownEditorRegistry {

    private final ConcurrentMap<Path, MarkdownFileEditor> editors = new ConcurrentHashMap<>();

    void register(MarkdownFileEditor editor) {
        editors.put(editor.getFile(), editor);
    }

    void remove(MarkdownFileEditor editor) {
        editors.remove(editor.getFile(), editor);
    }

    Optional<MarkdownFileEditor> find(Path file) {
        if (file == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(editors.get(file.toAbsolutePath().normalize()));
    }

    int size() {
        return editors.size();
    }
}
