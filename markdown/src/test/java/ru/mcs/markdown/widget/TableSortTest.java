package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TableSortTest {

    private static List<String> column(TableModel table, int column) {
        return java.util.stream.IntStream.range(0, table.rowCount()).mapToObj(row -> table.cell(row, column).strip())
                .toList();
    }

    @Test
    void numbersSortNumericallyAndEmptyCellsStayLastInBothDirections() {
        TableModel table = TableModel.parse("| Name | Qty |\n| --- | --- |\n| b | 10 |\n| a | 2 |\n| c |  |\n| d | 7 |");

        assertThat(column(table.sortBy(1, true), 0)).containsExactly("Name", "a", "d", "b", "c");
        assertThat(column(table.sortBy(1, false), 0)).containsExactly("Name", "b", "d", "a", "c");
    }

    @Test
    void textIgnoresCaseNumbersGoFirstAndDecimalCommasWork() {
        TableModel text = TableModel.parse("| T |\n| --- |\n| b |\n| A |\n| c |");
        TableModel mixed = TableModel.parse("| T |\n| --- |\n| abc |\n| 5 |\n| 1,5 |\n| 50% |");

        assertThat(column(text.sortBy(0, true), 0)).containsExactly("T", "A", "b", "c");
        assertThat(column(mixed.sortBy(0, true), 0)).containsExactly("T", "1,5", "5", "50%", "abc");
    }

    @Test
    void headerStaysFirstAndInvalidRequestsKeepTable() {
        TableModel table = TableModel.parse("| H | N |\n| :--- | ---: |\n| z | 1 |\n| y | 1 |");

        TableModel sorted = table.sortBy(1, false);

        assertThat(column(sorted, 0)).containsExactly("H", "z", "y");
        assertThat(sorted.alignment(1)).isEqualTo(TableModel.Alignment.RIGHT);
        assertThat(table.sortBy(5, true)).isSameAs(table);
    }
}
