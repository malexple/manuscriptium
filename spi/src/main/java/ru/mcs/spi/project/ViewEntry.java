package ru.mcs.spi.project;

import java.util.List;

/** Запись виртуального дерева: либо узел {@code node}, либо ссылка {@code file} на документ. */
public record ViewEntry(String node, String file, String overrideTitle, boolean visibility,
                 List<String> tags, List<ViewEntry> children) {

    public ViewEntry {
        tags = List.copyOf(tags);
        children = node != null ? children : List.of();
    }

    public static ViewEntry node(String title, String overrideTitle, boolean visibility,
                          List<String> tags, List<ViewEntry> children) {
        return new ViewEntry(title, null, overrideTitle, visibility, tags, children);
    }

    public static ViewEntry file(String path, String overrideTitle, boolean visibility, List<String> tags) {
        return new ViewEntry(null, path, overrideTitle, visibility, tags, List.of());
    }

    public boolean isNode() {
        return node != null;
    }

    public String title() {
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
