package ru.mcs.editor.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.editor.FileEditorProvider;

/** Встроенный провайдер обычных текстовых файлов; подключается через ServiceLoader. */
public final class TextFileEditorProvider implements FileEditorProvider {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final TextFileStore fileStore = new TextFileStore(MAX_BYTES);

    @Override
    public String getId() {
        return "editor.text";
    }

    @Override
    public int getPriority() {
        return -100;
    }

    @Override
    public boolean accept(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            return Files.size(file) <= MAX_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileEditor createEditor(Path file) throws IOException {
        if (!accept(file)) {
            throw new IOException("Not a readable regular text file within the size limit: " + file);
        }
        return new TextFileEditor(file, fileStore);
    }
}
