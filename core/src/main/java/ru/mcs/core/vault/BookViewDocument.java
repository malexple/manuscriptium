package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ru.mcs.spi.service.AtomicFileStore;

/** Сквозная проекция видимых Markdown-ссылок одного view или его поддерева. */
final class BookViewDocument {

    private final List<Chapter> chapters;

    private BookViewDocument(List<Chapter> chapters) {
        this.chapters = List.copyOf(chapters);
    }

    static BookViewDocument load(Path root, ViewDefinition view, List<Integer> nodePath,
                                 AtomicFileStore store) throws IOException {
        List<ViewEntry> entries = view.entries();
        if (!nodePath.isEmpty()) {
            ViewEntry selected = ViewOperations.entryAt(entries, nodePath);
            if (selected == null) {
                throw new IOException("View node not found: " + nodePath);
            }
            entries = selected.isNode() ? selected.children()
                    : List.of(selected);
        }
        List<Chapter> chapters = new ArrayList<>();
        flatten(root.toAbsolutePath().normalize(), entries, nodePath, chapters, store);
        return new BookViewDocument(chapters);
    }

    List<Chapter> chapters() {
        return chapters;
    }

    String markdown(Map<Path, String> edited) {
        StringBuilder result = new StringBuilder();
        for (Chapter chapter : chapters) {
            if (!result.isEmpty()) {
                if (result.charAt(result.length() - 1) != '\n') {
                    result.append('\n');
                }
                if (result.length() < 2 || result.charAt(result.length() - 2) != '\n') {
                    result.append('\n');
                }
            }
            result.append(edited.getOrDefault(chapter.file(), chapter.source()));
        }
        return result.toString();
    }

    void save(Map<Path, String> edited, AtomicFileStore store) throws IOException {
        for (Chapter chapter : chapters) {
            String value = edited.get(chapter.file());
            if (value != null && !value.equals(chapter.source())) {
                store.write(chapter.file(), value.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    void export(Path target, Map<Path, String> edited) throws IOException {
        // CREATE_NEW также отклоняет существующий symlink: экспорт никогда не перезаписывает чужой файл.
        Files.writeString(target, markdown(edited), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static void flatten(Path root, List<ViewEntry> entries, List<Integer> parentPath,
                                List<Chapter> result, AtomicFileStore store) throws IOException {
        for (int index = 0; index < entries.size(); index++) {
            ViewEntry entry = entries.get(index);
            if (!entry.visibility()) {
                continue;
            }
            List<Integer> path = new ArrayList<>(parentPath);
            path.add(index);
            if (entry.isNode()) {
                flatten(root, entry.children(), path, result, store);
                continue;
            }
            Path file = root.resolve(entry.file()).normalize();
            if (!file.startsWith(root)) {
                throw new IOException("Book chapter is outside the vault: " + entry.file());
            }
            result.add(new Chapter(path, file, entry.title(),
                    new String(store.read(file), StandardCharsets.UTF_8)));
        }
    }

    record Chapter(List<Integer> path, Path file, String title, String source) {
        Chapter {
            path = List.copyOf(path);
            file = file.toAbsolutePath().normalize();
        }
    }
}
