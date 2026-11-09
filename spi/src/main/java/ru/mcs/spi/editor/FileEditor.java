package ru.mcs.spi.editor;

import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JComponent;

/** Редактор одного файла. Все методы вызываются на EDT. */
public interface FileEditor {

    Path getFile();

    /** Короткое имя для вкладки (обычно имя файла). */
    String getName();

    JComponent getComponent();

    boolean isModified();

    /** Сохранить; после успешного сохранения isModified() == false и слушатели уведомлены. */
    void save() throws IOException;

    /** Слушатель вызывается при каждом изменении признака isModified(). */
    void addModifiedListener(Runnable listener);

    /** Освободить ресурсы. Вызывается при закрытии вкладки и выгрузке плагина. */
    default void dispose() {
    }
}
