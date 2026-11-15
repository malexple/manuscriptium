package ru.mcs.plugin.mdeditor.widget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TableModelTest {

    private static final String TABLE = "| Name | Value |\n|:-----|------:|\n| a | 1 |\n| b | 2 |";

    @Test
    void structuralOperationsKeepCellContentsAndAlignment() {
        TableModel table = TableModel.parse(TABLE);
        assertThat(table.rowCount()).isEqualTo(3);
        assertThat(table.columnCount()).isEqualTo(2);
        assertThat(table.alignment(1)).isEqualTo(TableModel.Alignment.RIGHT);

        TableModel changed = table.addRow(2).addColumn(1).align(1, TableModel.Alignment.CENTER)
                .setCell(3, 0, "c");
        assertThat(changed.rowCount()).isEqualTo(4);
        assertThat(changed.columnCount()).isEqualTo(3);
        assertThat(changed.cell(3, 0)).isEqualTo("c");
        assertThat(changed.source()).contains(":---:");
        String restored = changed.removeColumn(2).removeRow(3).align(1, TableModel.Alignment.RIGHT).source();
        assertThat(restored).contains("| a | 1 |").contains("| b | 2 |").contains("---:");
    }

    @Test
    void escapedPipeRemainsInsideCell() {
        TableModel table = TableModel.parse("| A | B |\n|---|---|\n| x\\|y | z |");
        assertThat(table.cell(1, 0)).isEqualTo("x\\|y");
        assertThat(table.setCell(1, 1, "a|b").cell(1, 1)).isEqualTo("a\\|b");
    }
}
