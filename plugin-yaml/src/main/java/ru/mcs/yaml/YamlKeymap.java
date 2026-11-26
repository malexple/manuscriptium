package ru.mcs.yaml;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;

/** Ставит сочетания области YAML на компонент редактора; может переустанавливаться при изменении настроек. */
final class YamlKeymap {

    private final JComponent component;
    private final Map<String, Runnable> commands;
    private final int mask = Shortcuts.defaultMask();
    private final List<KeyStroke> installed = new ArrayList<>();

    YamlKeymap(JComponent component, Map<String, Runnable> commands) {
        this.component = component;
        this.commands = commands;
    }

    /** Без сервиса действуют сочетания по умолчанию. */
    void install(KeymapService service) {
        uninstall();
        for (ShortcutBinding binding : YamlKeymapScopes.defaults()) {
            Runnable command = commands.get(binding.actionId());
            if (command == null) {
                continue;
            }
            String name = "yaml.editor." + binding.actionId();
            component.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    command.run();
                }
            });
            String shortcut = service == null ? binding.defaultShortcut()
                    : service.shortcut(YamlKeymapScopes.SCOPE, binding.actionId());
            KeyStroke key = parse(shortcut);
            if (key != null) {
                component.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
                installed.add(key);
            }
        }
    }

    void uninstall() {
        for (KeyStroke key : installed) {
            component.getInputMap(JComponent.WHEN_FOCUSED).remove(key);
        }
        installed.clear();
    }

    private KeyStroke parse(String shortcut) {
        try {
            return Shortcuts.parse(shortcut, mask);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
