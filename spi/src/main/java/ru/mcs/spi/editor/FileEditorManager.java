package ru.mcs.spi.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface FileEditorManager {

    void registerProvider(FileEditorProvider provider);

    /** Снимает провайдер и закрывает открытые им редакторы без подтверждения. */
    void unregisterProvider(FileEditorProvider provider);

    /** @return false, если нет подходящего провайдера или редактор не удалось создать */
    boolean openFile(Path file);

    /** @return false, если закрытие отменено пользователем (несохранённые изменения) */
    boolean closeFile(Path file);

    List<Path> getOpenFiles();

    Optional<Path> getSelectedFile();

    void selectFile(Path file);

    boolean isPinned(Path file);

    void setPinned(Path file, boolean pinned);

    void saveAll() throws IOException;
}
