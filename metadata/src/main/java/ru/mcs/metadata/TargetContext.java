package ru.mcs.metadata;

import java.util.List;
import java.util.Set;

/** Фактическая платформа и возможности текущего клиента. */
public record TargetContext(String target, Set<String> capabilities) {

    public TargetContext {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Target must not be blank");
        }
        capabilities = Set.copyOf(capabilities);
    }

    public static TargetContext desktop(String... capabilities) {
        return new TargetContext("desktop", Set.of(capabilities));
    }
}
