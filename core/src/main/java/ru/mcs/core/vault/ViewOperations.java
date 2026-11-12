package ru.mcs.core.vault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Операции только над ссылками YAML. Физические пути Markdown здесь не изменяются. */
final class ViewOperations {

    private ViewOperations() {
    }

    static boolean moveOrClone(ViewDefinition view, List<Integer> sourcePath,
                               List<Integer> targetPath, boolean clone) {
        if (sourcePath.isEmpty()) {
            return false;
        }
        ViewEntry source = entryAt(view.entries(), sourcePath);
        ViewEntry target = targetPath.isEmpty() ? null : entryAt(view.entries(), targetPath);
        if (source == null || source.isNode() || !targetPath.isEmpty() && target == null
                || target != null && !target.isNode()) {
            return false;
        }
        List<ViewEntry> sourceParent = parentOf(view.entries(), sourcePath);
        List<ViewEntry> targetChildren = target == null ? view.entries() : target.children();
        if (sourceParent == null || targetChildren == null || sourceParent == targetChildren) {
            return false;
        }
        for (ViewEntry sibling : targetChildren) {
            if (!sibling.isNode() && sibling.file().equals(source.file())) {
                return false;
            }
        }
        if (!clone && !sourceParent.remove(source)) {
            return false;
        }
        targetChildren.add(source);
        return true;
    }

    /** Добавляет ссылку из физического дерева; сам Markdown-файл остаётся неизменным. */
    static boolean addLink(ViewDefinition view, String file, List<Integer> targetPath) {
        ViewEntry target = targetPath.isEmpty() ? null : entryAt(view.entries(), targetPath);
        if (!targetPath.isEmpty() && target == null || target != null && !target.isNode()) {
            return false;
        }
        List<ViewEntry> targetChildren = target == null ? view.entries() : target.children();
        if (targetChildren == null) {
            return false;
        }
        for (ViewEntry sibling : targetChildren) {
            if (!sibling.isNode() && sibling.file().equals(file)) {
                return false;
            }
        }
        targetChildren.add(ViewEntry.file(file, null, true, List.of()));
        return true;
    }

    static boolean removeLink(ViewDefinition view, List<Integer> sourcePath, String expectedFile) {
        if (sourcePath.isEmpty()) {
            return false;
        }
        ViewEntry source = entryAt(view.entries(), sourcePath);
        List<ViewEntry> parent = parentOf(view.entries(), sourcePath);
        return source != null && !source.isNode() && source.file().equals(expectedFile)
                && parent != null && parent.remove(source);
    }

    static ViewEntry entryAt(List<ViewEntry> entries, List<Integer> path) {
        List<ViewEntry> current = entries;
        ViewEntry result = null;
        for (int index : path) {
            if (index < 0 || index >= current.size()) {
                return null;
            }
            result = current.get(index);
            current = result.children();
        }
        return result;
    }

    private static List<ViewEntry> parentOf(List<ViewEntry> entries, List<Integer> path) {
        if (path.size() == 1) {
            return path.getFirst() >= 0 && path.getFirst() < entries.size() ? entries : null;
        }
        ViewEntry parent = entryAt(entries, path.subList(0, path.size() - 1));
        return parent == null || !parent.isNode() ? null : parent.children();
    }

    /** Файл считается клоном, когда видимые ссылки на него находятся минимум в двух ветках views. */
    static Set<String> clonedFiles(List<ViewDefinition> views) {
        Map<String, Integer> counts = new HashMap<>();
        for (ViewDefinition view : views) {
            countVisible(view.entries(), counts);
        }
        Set<String> result = new HashSet<>();
        counts.forEach((path, count) -> {
            if (count > 1) {
                result.add(path);
            }
        });
        return Set.copyOf(result);
    }

    private static void countVisible(List<ViewEntry> entries, Map<String, Integer> counts) {
        for (ViewEntry entry : entries) {
            if (!entry.visibility()) {
                continue;
            }
            if (entry.isNode()) {
                countVisible(entry.children(), counts);
            } else {
                counts.merge(entry.file(), 1, Integer::sum);
            }
        }
    }

    static List<ViewEntry> copyEntries(List<ViewEntry> source) {
        List<ViewEntry> result = new ArrayList<>();
        for (ViewEntry entry : source) {
            result.add(entry.isNode()
                    ? ViewEntry.node(entry.node(), entry.overrideTitle(), entry.visibility(), entry.tags(),
                    copyEntries(entry.children()))
                    : ViewEntry.file(entry.file(), entry.overrideTitle(), entry.visibility(), entry.tags()));
        }
        return result;
    }
}
