package ru.mcs.spi.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/** Редактор одного файла. Методы, кроме фоновой части saveAsync(), вызываются на EDT. */
public interface FileEditor {

    Path getFile();

    /** Короткое имя для вкладки (обычно имя файла). */
    String getName();

    JComponent getComponent();

    boolean isModified();

    /** Сохранить; после успешного сохранения isModified() == false и слушатели уведомлены. */
    void save() throws IOException;

    /**
     * Асинхронное сохранение для UI host. Вызывается и завершается на EDT; реализация выполняет дисковый I/O вне EDT.
     * Реализация по умолчанию сохраняет бинарную совместимость со старыми редакторами через синхронный save().
     */
    default CompletableFuture<Void> saveAsync() {
        try {
            save();
            return CompletableFuture.completedFuture(null);
        } catch (IOException | RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Слушатель вызывается при каждом изменении признака isModified(). */
    void addModifiedListener(Runnable listener);

    /** Освободить ресурсы. Вызывается при закрытии вкладки и выгрузке плагина. */
    default void dispose() {
    }
}
