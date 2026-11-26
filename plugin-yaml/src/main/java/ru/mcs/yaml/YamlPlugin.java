package ru.mcs.yaml;

import java.util.function.UnaryOperator;
import javax.swing.SwingUtilities;
import ru.mcs.editor.view.CommonKeymap;
import ru.mcs.editor.view.TokenColors;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.swing.Plugin;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;
import ru.mcs.spi.swing.settings.SettingsGroups;

/** Внешний плагин: редактор .yml и .yaml с подсветкой, проверкой и настройками. */
public final class YamlPlugin implements Plugin {

    private final YamlSettings settings = new YamlSettings();
    private final YamlCodeStyleState codeStyle = new YamlCodeStyleState();
    private YamlFileEditorProvider provider;
    private ColorSchemeService colorScheme;
    private Runnable colorListener;
    private volatile TokenColors tokenColors = new YamlTokenColors();
    private KeymapService keymap;
    private Runnable keymapListener;

    @Override
    public void start(PluginContext context) {
        UnaryOperator<String> text = key -> context.i18n().get(key);
        context.state().register("settings", Scope.APPLICATION, settings);
        context.state().register("codeStyle", Scope.APPLICATION, codeStyle);

        colorScheme = context.services().find(ColorSchemeService.class).orElse(null);
        if (colorScheme != null) {
            colorScheme.registerGroup(YamlColorScheme.group(text));
            tokenColors = YamlColorScheme.colors(colorScheme);
            colorListener = () -> SwingUtilities.invokeLater(this::refresh);
            colorScheme.addChangeListener(colorListener);
        }
        keymap = context.services().find(KeymapService.class).orElse(null);
        if (keymap != null) {
            if (keymap.scopes(CommonKeymap.LANGUAGE_ID).isEmpty()) {
                CommonKeymap.register(keymap, text);
            }
            YamlKeymapScopes.register(keymap, text);
            keymapListener = () -> SwingUtilities.invokeLater(this::refresh);
            keymap.addChangeListener(keymapListener);
        }

        provider = new YamlFileEditorProvider(new YamlEditorServices(
                context.services().get(AtomicFileStore.class), context.i18n(), settings, codeStyle,
                () -> tokenColors, () -> keymap));
        context.editors().registerProvider(provider);

        context.settings().register(YamlSettingsPages.provider("yaml.settings.general", SettingsGroups.GENERAL,
                text, () -> new YamlGeneralConfigurable(settings, this::refresh, text)));
        context.settings().register(YamlSettingsPages.provider("yaml.settings.codestyle", SettingsGroups.CODE_STYLE,
                text, () -> new YamlCodeStyleConfigurable(codeStyle, text)));
        context.settings().register(YamlSettingsPages.provider("yaml.settings.smartkeys", SettingsGroups.SMART_KEYS,
                text, () -> keymap == null ? new YamlGeneralConfigurable(settings, this::refresh, text)
                        : keymap.createSettings(YamlKeymapScopes.LANGUAGE_ID)));
        context.log("YAML plugin started");
    }

    @Override
    public void stop() {
        if (colorScheme != null) {
            colorScheme.removeChangeListener(colorListener);
            colorScheme.unregisterGroup(YamlColorScheme.GROUP_ID);
        }
        if (keymap != null) {
            keymap.removeChangeListener(keymapListener);
            keymap.unregisterScopes(YamlKeymapScopes.LANGUAGE_ID);
        }
    }

    private void refresh() {
        if (provider != null) {
            provider.refresh();
        }
    }
}
