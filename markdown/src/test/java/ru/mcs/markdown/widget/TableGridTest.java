package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableGridTest {

    private static final int BASE = 10;
    private static final String TABLE = "| A | B |\n|:---|---:|\n| one | two |";
    private static final TableGrid.Layout LAYOUT = new TableGrid.Layout(0, 0, 200, 30,
            (text, header) -> text.length() * 7, List.of(TableModel.Alignment.LEFT, TableModel.Alignment.RIGHT));

    @Test
    void cellsKnowTheirSourceRanges() {
        TableGrid grid = TableGrid.parse(TABLE, BASE);
        int one = BASE + TABLE.indexOf("one");
        int two = BASE + TABLE.indexOf("two");

        assertThat(grid.rowCount()).isEqualTo(2);
        assertThat(grid.columnCount()).isEqualTo(2);
        assertThat(grid.cell(1, 0).start()).isEqualTo(one);
        assertThat(grid.cell(1, 0).end()).isEqualTo(one + 3);
        assertThat(grid.cell(1, 1).start()).isEqualTo(two);
        assertThat(grid.cell(0, 0).start()).isEqualTo(BASE + TABLE.indexOf("A"));
    }

    @Test
    void emptyCellsGetCaretPositionAfterPadding() {
        TableGrid grid = TableGrid.parse("|  |  |\n| :--- | :--- |\n|  |  |", BASE);

        assertThat(grid.cell(0, 0).start()).isEqualTo(BASE + 2);
        assertThat(grid.cell(0, 0).end()).isEqualTo(BASE + 2);
        assertThat(grid.cell(0, 1).start()).isEqualTo(BASE + 5);
    }

    @Test
    void stepsSkipDelimitersAndStopAtTableEdges() {
        TableGrid grid = TableGrid.parse(TABLE, BASE);
        int one = BASE + TABLE.indexOf("one");
        int two = BASE + TABLE.indexOf("two");
        int first = BASE + TABLE.indexOf("A");

        assertThat(grid.next(one + 3)).isEqualTo(two);
        assertThat(grid.previous(two)).isEqualTo(one + 3);
        assertThat(grid.next(one + 1)).isEqualTo(one + 2);
        assertThat(grid.next(two + 3)).isEqualTo(two + 3);
        assertThat(grid.previous(first)).isEqualTo(first);
        assertThat(grid.next(first + 1)).isEqualTo(BASE + TABLE.indexOf("B"));
    }

    @Test
    void caretRectFollowsAlignmentAndRow() {
        TableGrid grid = TableGrid.parse(TABLE, BASE);
        int one = BASE + TABLE.indexOf("one");
        int two = BASE + TABLE.indexOf("two");

        assertThat(grid.caretRect(one + 2, LAYOUT)).isEqualTo(new Rectangle(8 + 14, 34, 1, 22));
        assertThat(grid.caretRect(two, LAYOUT)).isEqualTo(new Rectangle(200 - 21 - 8, 34, 1, 22));
    }

    @Test
    void hitOffsetPicksNearestPositionAndClampsOutsidePoints() {
        TableGrid grid = TableGrid.parse(TABLE, BASE);
        int one = BASE + TABLE.indexOf("one");
        int two = BASE + TABLE.indexOf("two");

        assertThat(grid.hitOffset(new Point(22, 39), LAYOUT)).isEqualTo(one + 2);
        assertThat(grid.hitOffset(new Point(500, 40), LAYOUT)).isEqualTo(two + 3);
        assertThat(grid.hitOffset(new Point(5, -50), LAYOUT)).isEqualTo(BASE + TABLE.indexOf("A"));
    }

    @Test
    void escapedPipeStaysInsideCellAndIsShownAsSinglePipe() {
        String source = "| a\\|b | c |\n|---|---|\n| 1 | 2 |";
        TableGrid grid = TableGrid.parse(source, 0);
        TableGrid.Cell cell = grid.cell(0, 0);

        assertThat(grid.columnCount()).isEqualTo(2);
        assertThat(cell.end() - cell.start()).isEqualTo(4);
        assertThat(grid.caretRect(cell.start() + 3, LAYOUT).x).isEqualTo(8 + 2 * 7);
    }

    @Test
    void selectionCoversTextOfEachCell() {
        TableGrid grid = TableGrid.parse(TABLE, BASE);
        int one = BASE + TABLE.indexOf("one");

        assertThat(grid.selectionRects(one, one + 3, LAYOUT)).containsExactly(new Rectangle(8, 30, 21, 30));
        assertThat(grid.selectionRects(one, one, LAYOUT)).isEmpty();
    }
}
