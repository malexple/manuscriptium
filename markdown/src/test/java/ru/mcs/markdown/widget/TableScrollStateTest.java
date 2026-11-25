package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TableScrollStateTest {

    @Test
    void remembersOffsetPerTableAndForgetsZero() {
        TableScrollState state = new TableScrollState();

        state.set(10, 120);
        state.set(50, 30);
        assertThat(state.get(10)).isEqualTo(120);
        assertThat(state.get(50)).isEqualTo(30);
        assertThat(state.get(99)).isZero();

        state.set(10, 0);
        assertThat(state.get(10)).isZero();
        state.clear();
        assertThat(state.get(50)).isZero();
    }
}
