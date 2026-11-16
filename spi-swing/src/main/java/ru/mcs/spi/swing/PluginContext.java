package ru.mcs.spi.swing;

import ru.mcs.spi.event.MessageBus;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.ServiceRegistry;
import ru.mcs.spi.service.StateManager;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.editor.FileEditorManager;
import ru.mcs.spi.swing.settings.SettingsManager;
import ru.mcs.spi.swing.ui.StatusBar;
import ru.mcs.spi.swing.ui.ToolWindowManager;
import ru.mcs.spi.ui.NotificationManager;

/**
 * Единственный канал взаимодействия плагина с приложением (capability-модель).
 * Всё, что плагин зарегистрировал через эти менеджеры, снимается при его остановке.
 */
public interface PluginContext {

    String pluginId();

    void log(String message);

    ActionManager actions();

    ToolWindowManager toolWindows();

    FileEditorManager editors();

    StatusBar statusBar();

    NotificationManager notifications();

    MessageBus messageBus();

    /** Реестр сервисов приложения: плагины публикуют и находят сервисы друг друга. */
    ServiceRegistry services();

    /** Сохранение состояния плагина между запусками. */
    StateManager state();

    /** Реестр страниц настроек плагина. */
    SettingsManager settings();

    /** Локализованные строки плагина. */
    I18n i18n();
}
