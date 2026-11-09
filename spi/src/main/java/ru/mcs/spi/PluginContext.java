package ru.mcs.spi;

import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.editor.FileEditorManager;
import ru.mcs.spi.ui.ToolWindowManager;

/** Единственный канал взаимодействия плагина с приложением (capability-модель). */
public interface PluginContext {

    String pluginId();

    void log(String message);

    /** Менеджер действий, привязанный к плагину: регистрации снимаются при остановке плагина. */
    ActionManager actions();

    /** Менеджер панелей, привязанный к плагину: регистрации снимаются при остановке плагина. */
    ToolWindowManager toolWindows();

    /** Менеджер редакторов: провайдеры плагина снимаются (а их редакторы закрываются) при остановке плагина. */
    FileEditorManager editors();
}
