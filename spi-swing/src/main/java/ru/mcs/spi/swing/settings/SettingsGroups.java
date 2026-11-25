package ru.mcs.spi.swing.settings;

/**
 * ID групп настроек редактора, которые создаёт хост. Плагин языка или формата регистрирует свою страницу
 * обычным {@link ConfigurableProvider}, вернув одну из этих групп из {@link ConfigurableProvider#getParentId()}.
 */
public final class SettingsGroups {

    /** Editor. */
    public static final String EDITOR = "core.settings.editor";
    /** Editor → General. */
    public static final String GENERAL = "core.settings.editor.general";
    /** Editor → General → Smart Keys: поведение при вводе. */
    public static final String SMART_KEYS = "core.settings.editor.smartkeys";
    /** Editor → Code Style: форматирование (Reformat). */
    public static final String CODE_STYLE = "core.settings.editor.codestyle";
    /** Editor → Color Scheme: цвета токенов подсветки. */
    public static final String COLOR_SCHEME = "core.settings.editor.colors";

    private SettingsGroups() {
    }
}
