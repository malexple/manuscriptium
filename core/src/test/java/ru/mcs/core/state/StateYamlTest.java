package ru.mcs.core.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StateYamlTest {

    @Test
    void roundTripOfNestedStructures() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("name", "Привет");
        inner.put("flag", true);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("count", 3);
        state.put("big", 5_000_000_000L);
        state.put("ratio", 0.5);
        state.put("list", List.of("a", "b", 1));
        state.put("inner", inner);
        state.put("nothing", null);

        Map<String, Object> back = StateYaml.parse(StateYaml.dump(state));

        assertThat(back.get("count")).isEqualTo(3);
        assertThat(((Number) back.get("big")).longValue()).isEqualTo(5_000_000_000L);
        assertThat(back.get("ratio")).isEqualTo(0.5);
        assertThat(back.get("list")).isEqualTo(List.of("a", "b", 1));
        assertThat(((Map<?, ?>) back.get("inner")).get("name")).isEqualTo("Привет");
        assertThat(back).containsKey("nothing");
    }

    @Test
    void keysAreSortedForStableOutput() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("b", 1);
        state.put("a", 2);
        String text = StateYaml.dump(state);
        assertThat(text.indexOf("a:")).isLessThan(text.indexOf("b:"));
    }

    @Test
    void unsupportedValuesAreRejected() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("bad", new Object());
        assertThatThrownBy(() -> StateYaml.dump(state)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$.bad");
    }

    @Test
    void nonStringKeysAreRejected() {
        Map<Object, Object> weird = new LinkedHashMap<>();
        weird.put(1, "x");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("weird", weird);
        assertThatThrownBy(() -> StateYaml.dump(state)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonMappingRootGivesEmptyState() {
        assertThat(StateYaml.parse("- a\n- b\n")).isEmpty();
        assertThat(StateYaml.parse("")).isEmpty();
    }
}
