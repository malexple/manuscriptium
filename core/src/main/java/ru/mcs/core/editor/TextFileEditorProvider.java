package ru.mcs.core.editor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorProvider;

/** Запасной провайдер: любой обычный файл до 5 МБ, который строго декодируется как UTF-8. */
public final class TextFileEditorProvider implements FileEditorProvider {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    @Override
    public String getId() {
        return "core.text";
    }

    @Override
    public int getPriority() {
        return -100;
    }

    @Override
    public boolean accept(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileEditor createEditor(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        boolean crlf = text.contains("\r\n");
        if (crlf) {
            text = text.replace("\r\n", "\n");
        }
        return new TextFileEditor(file, text, crlf);
    }
}
