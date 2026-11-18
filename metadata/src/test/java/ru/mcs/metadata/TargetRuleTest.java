package ru.mcs.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TargetRuleTest {

    @Test
    void selectsCommonConcreteTargetsAndCapabilities() {
        TargetContext desktop = new TargetContext("desktop", Set.of("swing", "editor"));
        TargetContext android = new TargetContext("android", Set.of("compose"));

        assertThat(TargetRule.ALWAYS.matches(desktop)).isTrue();
        assertThat(new TargetRule(List.of("common"), Set.of(), Set.of()).matches(android)).isTrue();
        assertThat(new TargetRule(List.of("desktop"), Set.of(), Set.of()).matches(android)).isFalse();
        assertThat(new TargetRule(List.of(), Set.of("editor"), Set.of()).matches(desktop)).isTrue();
        assertThat(new TargetRule(List.of(), Set.of("editor"), Set.of()).matches(android)).isFalse();
        assertThat(new TargetRule(List.of(), Set.of(), Set.of("swing")).matches(desktop)).isFalse();
    }
}
