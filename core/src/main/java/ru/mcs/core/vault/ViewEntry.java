package ru.mcs.core.vault;

import java.util.List;

/** Запись виртуального дерева: либо узел {@code node}, либо ссылка {@code file} на Markdown. */
record ViewEntry(String node, String file, String overrideTitle, boolean visibility,
                 List<String> tags, List<ViewEntry> children) {

    ViewEntry {
        tags = List.copyOf(tags);
        children = node != null ? children : List.of();
    }

    static ViewEntry node(String title, String overrideTitle, boolean visibility,
                          List<String> tags, List<ViewEntry> children) {
        return new ViewEntry(title, null, overrideTitle, visibility, tags, children);
    }

    static ViewEntry file(String path, String overrideTitle, boolean visibility, List<String> tags) {
        return new ViewEntry(null, path, overrideTitle, visibility, tags, List.of());
    }

    boolean isNode() {
        return node != null;
    }

    String title() {
        if (overrideTitle != null && !overrideTitle.isBlank()) {
            return overrideTitle;
        }
        if (isNode()) {
            return node;
        }
        int slash = file.lastIndexOf('/');
        return slash >= 0 ? file.substring(slash + 1) : file;
    }
}
