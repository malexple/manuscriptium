package ru.mcs.core.action;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import javax.swing.KeyStroke;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.icon.Icons;
import ru.mcs.metadata.MetadataCatalog;
import ru.mcs.metadata.MetadataDocument;
import ru.mcs.metadata.TargetContext;
import ru.mcs.metadata.TargetRule;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.DefaultActionGroup;
import ru.mcs.spi.swing.action.Separator;

/** Swing/desktop renderer и incremental binder декларативных UI-описаний. */
public final class DesktopMetadata {

    private final ActionManagerImpl actions;
    private final MetadataCatalog catalog;
    private final TargetContext context;

    public DesktopMetadata(ActionManagerImpl actions, boolean macOs) {
        this(actions, MetadataCatalog.load(), macOs);
    }

    DesktopMetadata(ActionManagerImpl actions, MetadataCatalog catalog, boolean macOs) {
        this.actions = actions;
        this.catalog = catalog;
        Set<String> capabilities = macOs
                ? Set.of("swing", "macos")
                : Set.of("swing", "non-macos");
        this.context = TargetContext.desktop(capabilities.toArray(String[]::new));
    }

    /** Повторный вызов после регистрации реализаций добавляет только появившиеся связи. */
    public void bind() {
        registerGroups();
        decorateActions();
        bindChildren();
        bindParents();
    }

    public MetadataDocument.SettingsPage settingsPage(String id) {
        return catalog.contributions().stream()
                .flatMap(contribution -> contribution.document().settingsPages().stream())
                .filter(page -> page.id().equals(id) && page.applicability().matches(context))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Settings page is not available: " + id));
    }

    public KeyStroke keyStroke(String commandId) {
        return catalog.contributions().stream()
                .flatMap(contribution -> contribution.document().keymap().stream())
                .filter(binding -> binding.commandId().equals(commandId) && binding.applicability().matches(context))
                .findFirst()
                .map(binding -> parseKeyStroke(binding.keystroke()))
                .orElseThrow(() -> new IllegalArgumentException("Key binding is not available: " + commandId));
    }

    private void registerGroups() {
        for (MetadataCatalog.Contribution contribution : catalog.contributions()) {
            MetadataDocument document = contribution.document();
            for (MetadataDocument.Menu menu : document.menus()) {
                if (menu.applicability().matches(context) && actions.getAction(menu.id()) == null) {
                    actions.registerAction(menu.id(), new DefaultActionGroup(text(contribution, menu.textKey()), menu.popup()));
                }
            }
            for (MetadataDocument.Toolbar toolbar : document.toolbars()) {
                if (toolbar.applicability().matches(context) && actions.getAction(toolbar.id()) == null) {
                    actions.registerAction(toolbar.id(), new DefaultActionGroup("", false));
                }
            }
            for (MetadataDocument.Menu menu : document.menus()) {
                registerSeparators(menu.children());
            }
            for (MetadataDocument.Toolbar toolbar : document.toolbars()) {
                registerSeparators(toolbar.children());
            }
        }
    }

    private void registerSeparators(List<MetadataDocument.Entry> entries) {
        for (MetadataDocument.Entry entry : entries) {
            if (entry.isSeparator() && actions.getAction(entry.separatorId()) == null) {
                actions.registerAction(entry.separatorId(), new Separator());
            }
        }
    }

    private void decorateActions() {
        for (MetadataCatalog.Contribution contribution : catalog.contributions()) {
            for (MetadataDocument.Action description : contribution.document().actions()) {
                AnAction action = actions.getAction(description.id());
                if (action == null || action instanceof Separator) {
                    continue;
                }
                String text = text(contribution, description.textKey());
                if (text != null) {
                    action.getTemplatePresentation().setText(text);
                }
                String descriptionText = text(contribution, description.descriptionKey());
                if (descriptionText != null) {
                    action.getTemplatePresentation().setDescription(descriptionText);
                }
                if (description.icon() != null) {
                    action.getTemplatePresentation().setIcon(Icons.lucide(description.icon()));
                }
            }
        }
    }

    private void bindChildren() {
        boolean changed;
        do {
            changed = false;
            for (MetadataCatalog.Contribution contribution : catalog.contributions()) {
                MetadataDocument document = contribution.document();
                for (MetadataDocument.Menu menu : document.menus()) {
                    if (menu.applicability().matches(context)) {
                        changed |= bindEntries(menu.id(), menu.children());
                    }
                }
                for (MetadataDocument.Toolbar toolbar : document.toolbars()) {
                    if (toolbar.applicability().matches(context)) {
                        changed |= bindEntries(toolbar.id(), toolbar.children());
                    }
                }
            }
        } while (changed);
    }

    private boolean bindEntries(String parent, List<MetadataDocument.Entry> entries) {
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            MetadataDocument.Entry entry = entries.get(i);
            String id = entry.isSeparator() ? entry.separatorId() : entry.commandId();
            if (!applicable(id) || actions.getAction(id) == null || actions.childIds(parent).contains(id)) {
                continue;
            }
            // Не позволяем DefaultActionGroup деградировать BEFORE/AFTER до случайного LAST
            // во время incremental binding: дожидаемся появления anchor в этой же группе.
            if (entry.anchor() != null && !actions.childIds(parent).contains(entry.anchor())) {
                continue;
            }
            if (entry.isSeparator() && !previousCommandsAvailable(parent, entries, i)) {
                continue;
            }
            int children = actions.childIds(parent).size();
            add(parent, id, constraints(entry));
            changed |= actions.childIds(parent).size() != children;
        }
        return changed;
    }

    private void bindParents() {
        for (MetadataCatalog.Contribution contribution : catalog.contributions()) {
            MetadataDocument document = contribution.document();
            for (MetadataDocument.Menu menu : document.menus()) {
                if (menu.applicability().matches(context) && actions.getAction(menu.id()) != null) {
                    menu.parents().forEach(parent -> add(parent, menu.id(), Constraints.LAST));
                }
            }
            for (MetadataDocument.Action action : document.actions()) {
                if (action.applicability().matches(context) && actions.getAction(action.id()) != null
                        && !(actions.getAction(action.id()) instanceof Separator)) {
                    action.parents().forEach(parent -> add(parent, action.id(), Constraints.LAST));
                }
            }
        }
    }

    private void add(String parent, String id, Constraints constraints) {
        if (actions.getAction(parent) == null || actions.getAction(id) == null
                || actions.childIds(parent).contains(id)) {
            return;
        }
        actions.addToGroup(parent, id, constraints);
    }

    private boolean applicable(String id) {
        return catalog.contributions().stream()
                .flatMap(contribution -> contribution.document().actions().stream())
                .filter(action -> action.id().equals(id))
                .findFirst()
                .map(MetadataDocument.Action::applicability)
                .orElse(TargetRule.ALWAYS)
                .matches(context);
    }

    private boolean previousCommandsAvailable(String parent, List<MetadataDocument.Entry> entries, int separator) {
        for (int i = 0; i < separator; i++) {
            MetadataDocument.Entry previous = entries.get(i);
            if (!previous.isSeparator() && applicable(previous.commandId())
                    && (actions.getAction(previous.commandId()) == null
                    || !actions.childIds(parent).contains(previous.commandId()))) {
                return false;
            }
        }
        return true;
    }

    private static Constraints constraints(MetadataDocument.Entry entry) {
        return switch (entry.position()) {
            case "first" -> Constraints.FIRST;
            case "before" -> Constraints.before(entry.anchor());
            case "after" -> Constraints.after(entry.anchor());
            default -> Constraints.LAST;
        };
    }

    private static String text(MetadataCatalog.Contribution contribution, String key) {
        if (key == null || contribution.provider().i18nBaseName() == null) {
            return null;
        }
        ResourceBundle messages = contribution.provider().messages(Messages.locale());
        return messages.getString(key);
    }

    private static KeyStroke parseKeyStroke(String description) {
        int modifiers = 0;
        String key = null;
        for (String token : description.trim().split("\\s+")) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "shortcut" -> modifiers |= shortcutMask();
                case "shift" -> modifiers |= InputEvent.SHIFT_DOWN_MASK;
                case "ctrl", "control" -> modifiers |= InputEvent.CTRL_DOWN_MASK;
                case "alt", "option" -> modifiers |= InputEvent.ALT_DOWN_MASK;
                case "meta" -> modifiers |= InputEvent.META_DOWN_MASK;
                default -> {
                    if (key != null) {
                        throw new IllegalArgumentException("Invalid keystroke: " + description);
                    }
                    key = token;
                }
            }
        }
        if (key == null || key.length() != 1) {
            throw new IllegalArgumentException("Unsupported keystroke: " + description);
        }
        return KeyStroke.getKeyStroke(KeyEvent.getExtendedKeyCodeForChar(key.charAt(0)), modifiers);
    }

    private static int shortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException e) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }
}
