package ru.mcs.core.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Топологическая сортировка плагинов по depends-on с обнаружением пропусков и циклов. */
public final class PluginOrdering {

    public record OrderResult(List<PluginDescriptor> ordered, Map<String, String> errors) {
    }

    private PluginOrdering() {
    }

    public static OrderResult sort(Collection<PluginDescriptor> all) {
        Map<String, PluginDescriptor> byId = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (PluginDescriptor d : all) {
            if (byId.putIfAbsent(d.id(), d) != null) {
                errors.put(d.id(), "duplicate plugin id");
            }
        }
        List<PluginDescriptor> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (PluginDescriptor d : byId.values()) {
            visit(d, byId, done, stack, ordered, errors);
        }
        return new OrderResult(ordered, errors);
    }

    private static boolean visit(PluginDescriptor d, Map<String, PluginDescriptor> byId, Set<String> done,
                                 Set<String> stack, List<PluginDescriptor> ordered, Map<String, String> errors) {
        if (errors.containsKey(d.id())) {
            return false;
        }
        if (done.contains(d.id())) {
            return true;
        }
        if (!stack.add(d.id())) {
            errors.putIfAbsent(d.id(), "cyclic dependency");
            return false;
        }
        boolean ok = true;
        for (String dep : d.dependsOn()) {
            PluginDescriptor dd = byId.get(dep);
            if (dd == null) {
                errors.putIfAbsent(d.id(), "missing dependency (absent, disabled or incompatible): " + dep);
                ok = false;
                break;
            }
            if (!visit(dd, byId, done, stack, ordered, errors)) {
                errors.putIfAbsent(d.id(), "dependency failed: " + dep);
                ok = false;
                break;
            }
        }
        stack.remove(d.id());
        if (ok) {
            done.add(d.id());
            ordered.add(d);
        }
        return ok;
    }
}
