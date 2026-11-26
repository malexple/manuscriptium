package ru.mcs.markdown.editor.action;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import ru.mcs.editor.action.EditorContext;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;

/**
 * Локальные клавиши редактора одной области ({@link MarkdownKeymapScopes}); делегируют в общий ActionManager.
 * Без {@link KeymapService} действуют сочетания по умолчанию; с сервисом — настроенные пользователем.
 */
public final class TyporaKeymap {

    private final ActionManager actions;
    private final int shortcutMask;
    private final KeymapService keymap;
    private final String scopeId;
    private final List<Installed> installed = new ArrayList<>();

    public TyporaKeymap(ActionManager actions) {
        this(actions, Shortcuts.defaultMask(), null, MarkdownKeymapScopes.CANVAS);
    }

    public TyporaKeymap(ActionManager actions, KeymapService keymap, String scopeId) {
        this(actions, Shortcuts.defaultMask(), keymap, scopeId);
    }

    TyporaKeymap(ActionManager actions, int shortcutMask) {
        this(actions, shortcutMask, null, MarkdownKeymapScopes.CANVAS);
    }

    TyporaKeymap(ActionManager actions, int shortcutMask, KeymapService keymap, String scopeId) {
        this.actions = actions;
        this.shortcutMask = shortcutMask;
        this.keymap = keymap;
        this.scopeId = scopeId;
    }

    /** Ставит клавиши области на компонент редактора; прежние клавиши этого экземпляра снимаются. */
    public void install(EditorContext canvas) {
        uninstall();
        JComponent component = canvas.component();
        for (ShortcutBinding binding : MarkdownKeymapScopes.defaults(scopeId)) {
            String shortcut = keymap == null ? binding.defaultShortcut()
                    : keymap.shortcut(scopeId, binding.actionId());
            bind(component, parse(shortcut), binding.actionId());
        }
    }

    public void uninstall() {
        for (Installed entry : installed) {
            if (entry.key() != null) {
                entry.component().getInputMap(JComponent.WHEN_FOCUSED).remove(entry.key());
            }
            entry.component().getActionMap().remove(entry.actionId());
        }
        installed.clear();
    }

    private KeyStroke parse(String shortcut) {
        try {
            return Shortcuts.parse(shortcut, shortcutMask);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private void bind(JComponent component, KeyStroke key, String actionId) {
        if (key != null) {
            component.getInputMap(JComponent.WHEN_FOCUSED).put(key, actionId);
        }
        component.getActionMap().put(actionId, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                EditorActionDispatcher.perform(actions, actionId, ActionPlaces.MAIN_MENU);
            }
        });
        installed.add(new Installed(component, key, actionId));
    }

    private record Installed(JComponent component, KeyStroke key, String actionId) {
    }
}
