package ru.mcs.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import ru.mcs.metadata.MetadataDocument.Entry;
import ru.mcs.metadata.MetadataDocument.Field;
import ru.mcs.metadata.MetadataDocument.SettingsPage;

/** Проверяет документ в целом и ссылки между независимыми модулями. */
public final class MetadataValidator {

    private static final int FORMAT_VERSION = 1;
    private static final Set<String> TARGETS = Set.of("common", "desktop", "android", "ios");
    private static final Set<String> POSITIONS = Set.of("first", "last", "before", "after");
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right");
    private static final Set<String> ANCHORS = Set.of("left", "right", "bottom", "top");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern CAPABILITY = Pattern.compile("[a-z][a-z0-9-]*");

    private MetadataValidator() {
    }

    public static void validate(Map<String, MetadataDocument> documents) {
        Set<String> commandIds = new HashSet<>();
        Set<String> groupIds = new HashSet<>();
        Set<String> separators = new HashSet<>();
        documents.forEach((owner, document) -> {
            if (document.formatVersion() != FORMAT_VERSION) {
                throw error(owner, "Unsupported formatVersion: " + document.formatVersion());
            }
            document.actions().forEach(action -> {
                id(owner, action.id());
                unique(owner, commandIds, action.id(), "command");
                applicability(owner, action.applicability());
            });
            document.menus().forEach(menu -> {
                id(owner, menu.id());
                unique(owner, commandIds, menu.id(), "command");
                unique(owner, groupIds, menu.id(), "group");
                applicability(owner, menu.applicability());
            });
            document.toolbars().forEach(toolbar -> {
                id(owner, toolbar.id());
                unique(owner, commandIds, toolbar.id(), "command");
                unique(owner, groupIds, toolbar.id(), "group");
                applicability(owner, toolbar.applicability());
            });
            document.statusBar().forEach(widget -> {
                id(owner, widget.id());
                applicability(owner, widget.applicability());
                if (!ALIGNMENTS.contains(widget.alignment())) {
                    throw error(owner, "Unknown status alignment: " + widget.alignment());
                }
            });
            document.settingsPages().forEach(page -> settingsPage(owner, page));
            document.keymap().forEach(binding -> {
                id(owner, binding.commandId());
                if (binding.keystroke().isBlank()) {
                    throw error(owner, "Blank keystroke for " + binding.commandId());
                }
                applicability(owner, binding.applicability());
            });
            document.panels().forEach(panel -> {
                id(owner, panel.id());
                id(owner, panel.commandId());
                applicability(owner, panel.applicability());
                if (panel.anchor() != null && !ANCHORS.contains(panel.anchor())) {
                    throw error(owner, "Unknown panel anchor: " + panel.anchor());
                }
            });
            document.menus().forEach(menu -> separators.addAll(entries(owner, menu.id(), menu.children())));
            document.toolbars().forEach(toolbar -> separators.addAll(entries(owner, toolbar.id(), toolbar.children())));
        });

        Set<String> allCommands = new HashSet<>(commandIds);
        // Именованный separator может быть описан как action с собственным TargetRule.
        allCommands.addAll(separators);
        documents.forEach((owner, document) -> {
            document.actions().forEach(action -> action.parents().forEach(parent ->
                    reference(owner, groupIds, parent, "action parent")));
            document.menus().forEach(menu -> {
                menu.parents().forEach(parent -> reference(owner, groupIds, parent, "menu parent"));
                menu.children().forEach(entry -> entryReference(owner, allCommands, entry));
            });
            document.toolbars().forEach(toolbar -> toolbar.children().forEach(entry ->
                    entryReference(owner, allCommands, entry)));
            document.statusBar().forEach(widget -> reference(owner, commandIds, widget.commandId(), "status command"));
            document.keymap().forEach(binding -> reference(owner, commandIds, binding.commandId(), "keymap command"));
            document.panels().forEach(panel -> reference(owner, commandIds, panel.commandId(), "panel command"));
        });
    }

    private static Set<String> entries(String owner, String group, List<Entry> entries) {
        Set<String> siblings = new HashSet<>();
        Set<String> separators = new LinkedHashSet<>();
        for (Entry entry : entries) {
            String id = entry.isSeparator() ? entry.separatorId() : entry.commandId();
            if ((entry.commandId() == null) == (entry.separatorId() == null)) {
                throw error(owner, "Entry in " + group + " must contain exactly one of command/separator");
            }
            id(owner, id);
            unique(owner, siblings, id, "sibling entry");
            if (!POSITIONS.contains(entry.position())) {
                throw error(owner, "Unknown entry position: " + entry.position());
            }
            if (entry.position().equals("before") || entry.position().equals("after")) {
                if (entry.anchor() == null) {
                    throw error(owner, "Entry " + id + " requires an anchor");
                }
            } else if (entry.anchor() != null) {
                throw error(owner, "Entry " + id + " has an anchor without before/after position");
            }
            if (entry.isSeparator()) {
                separators.add(id);
            }
        }
        for (Entry entry : entries) {
            if (entry.anchor() != null && !siblings.contains(entry.anchor())) {
                throw error(owner, "Unknown entry anchor: " + entry.anchor());
            }
        }
        return separators;
    }

    private static void entryReference(String owner, Set<String> commands, Entry entry) {
        if (!entry.isSeparator()) {
            reference(owner, commands, entry.commandId(), "entry command");
        }
    }

    private static void settingsPage(String owner, SettingsPage page) {
        id(owner, page.id());
        applicability(owner, page.applicability());
        Set<String> fields = new HashSet<>();
        for (Field field : page.fields()) {
            id(owner, field.id());
            unique(owner, fields, field.id(), "settings field");
            if (field.stateKey().isBlank()) {
                throw error(owner, "Blank stateKey in " + page.id() + "." + field.id());
            }
            Set<String> values = new HashSet<>();
            field.options().forEach(option -> unique(owner, values, option.value(), "option value"));
            if (field.type() == MetadataDocument.FieldType.CHOICE && field.options().isEmpty()) {
                throw error(owner, "Choice field has no options: " + page.id() + "." + field.id());
            }
        }
    }

    private static void applicability(String owner, TargetRule rule) {
        rule.targets().forEach(target -> {
            if (!TARGETS.contains(target)) {
                throw error(owner, "Unknown target: " + target);
            }
        });
        List<String> capabilities = new ArrayList<>(rule.requiredCapabilities());
        capabilities.addAll(rule.excludedCapabilities());
        capabilities.forEach(capability -> {
            if (!CAPABILITY.matcher(capability).matches()) {
                throw error(owner, "Invalid capability: " + capability);
            }
        });
        Set<String> intersection = new HashSet<>(rule.requiredCapabilities());
        intersection.retainAll(rule.excludedCapabilities());
        if (!intersection.isEmpty()) {
            throw error(owner, "Capabilities both required and excluded: " + intersection);
        }
    }

    private static void reference(String owner, Set<String> ids, String id, String kind) {
        if (!ids.contains(id)) {
            throw error(owner, "Unknown " + kind + ": " + id);
        }
    }

    private static void id(String owner, String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw error(owner, "Invalid ID: " + id);
        }
    }

    private static void unique(String owner, Set<String> ids, String id, String kind) {
        if (!ids.add(id)) {
            throw error(owner, "Duplicate " + kind + " ID: " + id);
        }
    }

    private static MetadataException error(String owner, String message) {
        return new MetadataException(owner + ": " + message);
    }
}
