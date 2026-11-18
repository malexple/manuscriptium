package ru.mcs.metadata;

import java.util.List;
import java.util.Set;

/** Версионированное, UI-независимое описание возможностей модуля. */
public record MetadataDocument(
        int formatVersion,
        List<Action> actions,
        List<Menu> menus,
        List<Toolbar> toolbars,
        List<StatusWidget> statusBar,
        List<SettingsPage> settingsPages,
        List<KeyBinding> keymap,
        List<Panel> panels) {

    public MetadataDocument {
        actions = List.copyOf(actions);
        menus = List.copyOf(menus);
        toolbars = List.copyOf(toolbars);
        statusBar = List.copyOf(statusBar);
        settingsPages = List.copyOf(settingsPages);
        keymap = List.copyOf(keymap);
        panels = List.copyOf(panels);
    }

    public record Action(String id, String textKey, String descriptionKey, String icon,
                         List<String> parents, TargetRule applicability) {
        public Action {
            parents = List.copyOf(parents);
        }
    }

    public record Menu(String id, String textKey, boolean popup, List<String> parents,
                       List<Entry> children, TargetRule applicability) {
        public Menu {
            parents = List.copyOf(parents);
            children = List.copyOf(children);
        }
    }

    public record Toolbar(String id, List<Entry> children, TargetRule applicability) {
        public Toolbar {
            children = List.copyOf(children);
        }
    }

    /** Ровно одно из commandId/separatorId заполнено. */
    public record Entry(String commandId, String separatorId, String position, String anchor) {

        public static Entry command(String id) {
            return new Entry(id, null, "last", null);
        }

        public static Entry separator(String id) {
            return new Entry(null, id, "last", null);
        }

        public boolean isSeparator() {
            return separatorId != null;
        }
    }

    public record StatusWidget(String id, String commandId, String alignment, TargetRule applicability) {
    }

    public record SettingsPage(String id, String titleKey, List<Field> fields, TargetRule applicability) {
        public SettingsPage {
            fields = List.copyOf(fields);
        }
    }

    public record Field(String id, FieldType type, String labelKey, String stateKey,
                        String noteKey, List<Option> options) {
        public Field {
            options = List.copyOf(options);
        }
    }

    public record Option(String value, String labelKey) {
    }

    public enum FieldType {
        CHOICE, TEXT, BOOLEAN, INTEGER
    }

    public record KeyBinding(String commandId, String keystroke, TargetRule applicability) {
    }

    public record Panel(String id, String titleKey, String commandId, String anchor,
                        String icon, TargetRule applicability) {
    }
}
