package ru.mcs.core.plugin;

import java.util.List;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Декларативные вклады плагина из секции {@code contributions} файла plugin.yml. */
public record Contributions(
        List<ActionGroupDecl> actionGroups,
        List<ActionDecl> actions,
        List<ToolWindowDecl> toolWindows,
        List<EditorDecl> fileEditors) {

    public static final Contributions NONE = new Contributions(List.of(), List.of(), List.of(), List.of());

    public boolean isEmpty() {
        return actionGroups.isEmpty() && actions.isEmpty() && toolWindows.isEmpty() && fileEditors.isEmpty();
    }

    public record ActionGroupDecl(String id, String text, boolean popup, List<String> groups) {
    }

    public record ActionDecl(String id, String text, List<String> groups) {
    }

    public record ToolWindowDecl(String id, String title, ToolWindowAnchor anchor, boolean showByDefault) {
    }

    public record EditorDecl(String id, List<String> patterns, int priority) {
    }
}
