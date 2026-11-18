package ru.mcs.metadata;

import java.util.List;
import java.util.Set;

/** Декларативное правило доступности элемента на клиенте. */
public record TargetRule(List<String> targets, Set<String> requiredCapabilities,
                         Set<String> excludedCapabilities) {

    public static final TargetRule ALWAYS = new TargetRule(List.of(), Set.of(), Set.of());

    public TargetRule {
        targets = List.copyOf(targets);
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        excludedCapabilities = Set.copyOf(excludedCapabilities);
    }

    public boolean matches(TargetContext context) {
        boolean targetMatches = targets.isEmpty()
                || targets.contains("common")
                || targets.contains(context.target());
        return targetMatches
                && context.capabilities().containsAll(requiredCapabilities)
                && context.capabilities().stream().noneMatch(excludedCapabilities::contains);
    }
}
