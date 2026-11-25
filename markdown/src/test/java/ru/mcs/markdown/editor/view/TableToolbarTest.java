package ru.mcs.markdown.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableToolbarTest {

    @Test
    void buttonsFitInsideAreaWithoutOverlapAndKeepGroupsOnTheirSides() {
        Rectangle area = new Rectangle(10, 5, 400, TableToolbar.HEIGHT);
        List<TableToolbar.Slot> slots = TableToolbar.slots(area);

        assertThat(slots).hasSize(6);
        for (TableToolbar.Slot slot : slots) {
            assertThat(area.contains(slot.bounds())).isTrue();
        }
        for (int first = 0; first < slots.size(); first++) {
            for (int second = first + 1; second < slots.size(); second++) {
                assertThat(slots.get(first).bounds().intersects(slots.get(second).bounds())).isFalse();
            }
        }
        assertThat(slots.getFirst().action()).isEqualTo(TableToolbar.Action.RESIZE);
        assertThat(slots.getLast().action()).isEqualTo(TableToolbar.Action.DELETE);
        assertThat(slots.getLast().bounds().x + slots.getLast().bounds().width).isEqualTo(area.x + area.width);
    }
}
