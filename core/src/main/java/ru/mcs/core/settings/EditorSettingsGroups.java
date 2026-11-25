package ru.mcs.core.settings;

import ru.mcs.spi.swing.settings.SettingsGroups;
import ru.mcs.spi.swing.settings.SettingsManager;

/** Группы настроек редактора: к ним плагины подключают страницы своих языков и форматов. */
public final class EditorSettingsGroups {

    private EditorSettingsGroups() {
    }

    public static void register(SettingsManager settings) {
        settings.register(new GroupSettingsProvider(SettingsGroups.EDITOR, "settings.editor", null));
        settings.register(new GroupSettingsProvider(SettingsGroups.GENERAL, "settings.editor.general",
                SettingsGroups.EDITOR));
        settings.register(new GroupSettingsProvider(SettingsGroups.SMART_KEYS, "settings.editor.smartkeys",
                SettingsGroups.GENERAL));
        settings.register(new GroupSettingsProvider(SettingsGroups.CODE_STYLE, "settings.editor.codestyle",
                SettingsGroups.EDITOR));
    }
}
