package ru.mcs.spi.editor;

import java.io.IOException;
import java.nio.file.Path;

/** Фабрика редакторов. Среди подходящих провайдеров выбирается с максимальным приоритетом. */
public interface FileEditorProvider {

    String getId();

    boolean accept(Path file);

    FileEditor createEditor(Path file) throws IOException;

    /** Чем больше — тем приоритетнее. Встроенный текстовый редактор имеет -100. */
    default int getPriority() {
        return 0;
    }
}
