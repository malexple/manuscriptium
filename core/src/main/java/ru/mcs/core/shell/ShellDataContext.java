package ru.mcs.core.shell;

import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.action.DataKey;
import ru.mcs.spi.editor.EditorDataKeys;

/** Контекст данных оболочки: пока поставляет активный файл. */
public final class ShellDataContext implements DataContext {

    private final FileEditorManagerImpl editors;

    public ShellDataContext(FileEditorManagerImpl editors) {
        this.editors = editors;
    }

    @Override
    public <T> T get(DataKey<T> key) {
        if (EditorDataKeys.ACTIVE_FILE.equals(key)) {
            return key.type().cast(editors.getSelectedFile().orElse(null));
        }
        return null;
    }
}
