package ru.mcs.spi.swing;

/**
 * Встроенный модуль продукта, который компонуется приложением явно и не является внешним JAR-плагином.
 * Такой модуль не имеет plugin.yml, не создаёт отдельный ModuleLayer и не отображается в Settings → Plugins.
 * Регистрации выполняются через тот же capability-контекст и снимаются после {@link #stop()}.
 */
public interface BuiltInModule {

    /** Стабильный идентификатор владельца регистраций и состояния. */
    String id();

    /** База .properties-ресурсов внутри модуля или null, если локализация не нужна. */
    default String i18nBaseName() {
        return null;
    }

    /** Вызывается на EDT для UI и до восстановления вкладок Project. */
    void start(PluginContext context) throws Exception;

    /** Освобождает executors и другие ресурсы; снятие регистраций выполняет core. */
    void stop() throws Exception;
}
