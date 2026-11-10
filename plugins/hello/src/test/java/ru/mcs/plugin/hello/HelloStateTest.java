package ru.mcs.plugin.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HelloStateTest {

    @Test
    void counterUsesConfiguredStepAndStateSurvivesReload() {
        HelloState state = new HelloState();
        state.setStep(5);
        state.increment();
        state.increment();

        HelloState restored = new HelloState();
        restored.loadState(state.getState());

        assertThat(restored.clicks()).isEqualTo(10);
        assertThat(restored.step()).isEqualTo(5);
    }

    @Test
    void invalidPersistedStepIsIgnoredAndInvalidNewStepIsRejected() {
        HelloState state = new HelloState();
        state.loadState(Map.of("step", 0));
        assertThat(state.step()).isEqualTo(1);

        assertThatThrownBy(() -> state.setStep(101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
