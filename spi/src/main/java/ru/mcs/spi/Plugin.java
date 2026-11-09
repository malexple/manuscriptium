package ru.mcs.spi;

import javax.swing.JComponent;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.editor.FileEditorProvider;

/**
 * Точка входа плагина. Реализация объявляется в module-info плагина:
 * {@code provides ru.mcs.spi.Plugin with com.example.MyPlugin;}
 *
 * <p>Вклады, объявленные в секции {@code contributions} файла plugin.yml, хост регистрирует заранее
 * (меню, панели и т. д. видны сразу), а сами объекты запрашивает у плагина через методы {@code create*}
 * только при первом использовании — после {@link #start}. Один и тот же ID нельзя объявлять в plugin.yml
 * и одновременно регистрировать в {@code start()}.
 */
public interface Plugin {

    void start(PluginContext context) throws Exception;

    default void stop() {
    }

    /** Создаёт действие, объявленное в plugin.yml (contributions.actions). */
    default AnAction createAction(String id) {
        return null;
    }

    /** Создаёт содержимое панели, объявленной в plugin.yml (contributions.toolWindows). */
    default JComponent createToolWindowContent(String id) {
        return null;
    }

    /** Создаёт провайдер редакторов, объявленный в plugin.yml (contributions.fileEditors). */
    default FileEditorProvider createFileEditorProvider(String id) {
        return null;
    }
}
