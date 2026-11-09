package ru.mcs.spi.editor;

import java.nio.file.Path;
import ru.mcs.spi.action.DataKey;

/** Ключи DataContext, которые поставляет область редактора. */
public final class EditorDataKeys {

    /** Файл активной вкладки или null. */
    public static final DataKey<Path> ACTIVE_FILE = DataKey.create("mcs.activeFile", Path.class);

    private EditorDataKeys() {
    }
}
