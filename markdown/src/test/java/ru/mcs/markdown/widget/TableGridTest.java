package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableGridTest {

    private static final int BASE = 10;
    private static final String TABLE = "| A | B |\n|:---|---:|\n| one | two |";
    private static final TableGrid.Layout LAYOUT = new TableGrid.Layout(0, 0, 200, 18,
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

        assertThat(grid.caretRect(one + 2, LAYOUT)).isEqualTo(new Rectangle(8 + 14, 36, 1, 18));
        assertThat(grid.caretRect(two, LAYOUT)).isEqualTo(new Rectangle(200 - 21 - 8, 36, 1, 18));
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
    void caretAfterTrailingSpaceMovesRightOfText() {
        TableGrid grid = TableGrid.parse("| a  | b |\n|---|---|\n| 1 | 2 |", 0);
        TableGrid.Cell cell = grid.cell(0, 0);

        assertThat(cell.end() - cell.start()).isEqualTo(1);
        assertThat(grid.caretRect(cell.end() + 1, LAYOUT).x).isEqualTo(8 + 2 * 7);
        assertThat(grid.next(cell.end() + 1)).isEqualTo(grid.cell(0, 1).start());
    }

    @Test
    void stepsTreatEscapedPipeAsSingleCharacter() {
        TableGrid grid = TableGrid.parse("| a\\|b | c |\n|---|---|\n| 1 | 2 |", 0);
        TableGrid.Cell cell = grid.cell(0, 0);
        int afterPipe = cell.start() + 3;

        assertThat(grid.previous(afterPipe)).isEqualTo(cell.start() + 1);
        assertThat(grid.next(cell.start() + 1)).isEqualTo(afterPipe);
    }

    private static final TableGrid.Layout NARROW = new TableGrid.Layout(0, 0, 100, 18,
            (text, header) -> text.length() * 7, List.of(TableModel.Alignment.LEFT, TableModel.Alignment.LEFT));
    private static final String WRAPPED = "| aaaa bbbb cccc | b |\n|---|---|\n| x | y |";

    @Test
    void columnsShareWidthByContentLikeAutoTableLayout() {
        TableGrid grid = TableGrid.parse(WRAPPED, 0);
        TableGrid.Geometry wide = grid.geometry(new TableGrid.Layout(0, 0, 200, 18,
                (text, header) -> text.length() * 7, List.of(TableModel.Alignment.LEFT, TableModel.Alignment.LEFT)));

        assertThat(wide.edge(1)).isBetween(160, 170);
        assertThat(wide.edge(2)).isEqualTo(200);
        assertThat(wide.top(1)).isEqualTo(30);
    }

    @Test
    void longTextWrapsAtWordsWhenColumnCannotShrinkAnyMore() {
        TableGrid grid = TableGrid.parse(WRAPPED, 0);
        TableGrid.Geometry geometry = grid.geometry(NARROW);
        TableGrid.Cell cell = grid.cell(0, 0);
        int bbbb = cell.start() + "aaaa ".length();

        assertThat(geometry.lines(0, 0)).hasSize(3);
        assertThat(geometry.top(1)).isEqualTo(3 * 18 + 12);
        assertThat(grid.caretRect(bbbb, NARROW)).isEqualTo(new Rectangle(8, 24, 1, 18));
        assertThat(grid.hitOffset(new Point(8 + 14, 29), NARROW)).isEqualTo(bbbb + 2);
    }

    @Test
    void wideTableKeepsReadableColumnsAndOverflowsInsteadOfBreakingWords() {
        TableGrid grid = TableGrid.parse("| abcdefghijkl | x |\n|---|---|\n| 1 | 2 |", 0);
        TableGrid.Geometry geometry = grid.geometry(NARROW);

        assertThat(geometry.lines(0, 0)).hasSize(1);
        assertThat(geometry.edge(1)).isEqualTo(100);
        assertThat(geometry.width()).isEqualTo(100 + TableGrid.MIN_COLUMN);
    }

    @Test
    void revealScrollMovesViewToCaretAndBack() {
        String source = "| aaaaaaaaaa | bbbbbbbbbb | cccccccccc | dddddddddd |\n|---|---|---|---|\n| 1 | 2 | 3 | 4 |";
        TableGrid grid = TableGrid.parse(source, 0);
        TableGrid.Layout layout = new TableGrid.Layout(0, 0, 100, 18, (text, header) -> text.length() * 7,
                List.of(TableModel.Alignment.LEFT, TableModel.Alignment.LEFT, TableModel.Alignment.LEFT,
                        TableModel.Alignment.LEFT));
        int fourth = source.indexOf("dddddddddd");
        int maxScroll = grid.geometry(layout).width() - 100;

        int toEnd = grid.revealScroll(fourth + 5, 0, 100, layout);
        int back = grid.revealScroll(source.indexOf("aaaaaaaaaa"), toEnd, 100, layout);

        assertThat(toEnd).isGreaterThan(200).isLessThanOrEqualTo(maxScroll);
        assertThat(back).isZero();
    }

    @Test
    void selectionCoversTextOfEachCell() {
        TableGrid grid = TableGrid.parse(TABLE, BASE);
        int one = BASE + TABLE.indexOf("one");

        assertThat(grid.selectionRects(one, one + 3, LAYOUT)).containsExactly(new Rectangle(8, 36, 21, 18));
        assertThat(grid.selectionRects(one, one, LAYOUT)).isEmpty();
    }
}
