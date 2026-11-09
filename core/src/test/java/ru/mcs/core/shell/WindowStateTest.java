package ru.mcs.core.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowStateTest {

    @Test
    void startsWithoutBounds() {
        WindowState state = new WindowState();
        assertThat(state.bounds()).isNull();
        assertThat(state.getState()).containsEntry("maximized", false).doesNotContainKey("x");
    }

    @Test
    void stateRoundTrip() {
        WindowState first = new WindowState();
        first.update(new Rectangle(10, 20, 900, 600), false);

        WindowState second = new WindowState();
        second.loadState(first.getState());

        assertThat(second.bounds()).isEqualTo(new Rectangle(10, 20, 900, 600));
        assertThat(second.isMaximized()).isFalse();
    }

    @Test
    void maximizingKeepsNormalBounds() {
        WindowState state = new WindowState();
        state.update(new Rectangle(10, 20, 900, 600), false);
        state.update(new Rectangle(0, 0, 1920, 1080), true);
        assertThat(state.bounds()).isEqualTo(new Rectangle(10, 20, 900, 600));
        assertThat(state.isMaximized()).isTrue();
    }

    @Test
    void garbageAndTinyBoundsAreIgnored() {
        WindowState state = new WindowState();
        state.loadState(Map.of("x", "oops", "y", 1, "width", 50, "height", 50));
        assertThat(state.bounds()).isNull();
    }

    @Test
    void offScreenBoundsAreDetected() {
        List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080));
        assertThat(WindowState.intersectsEnough(new Rectangle(100, 100, 800, 600), screens)).isTrue();
        assertThat(WindowState.intersectsEnough(new Rectangle(5000, 100, 800, 600), screens)).isFalse();
        assertThat(WindowState.intersectsEnough(new Rectangle(1900, 1070, 800, 600), screens)).isFalse();
    }
}
