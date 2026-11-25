package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TableModelEditingTest {

    private static final String SOURCE = "| A | B |\n|:---|---:|\n| one | two |";

    @Test
    void resizeKeepsContentAndAlignmentsAndFillsNewCells() {
        TableModel bigger = TableModel.parse(SOURCE).resize(3, 3);

        assertThat(bigger.columnCount()).isEqualTo(3);
        assertThat(bigger.rowCount()).isEqualTo(3);
        assertThat(bigger.cell(1, 0)).isEqualTo("one");
        assertThat(bigger.cell(2, 0)).isEmpty();
        assertThat(bigger.alignment(1)).isEqualTo(TableModel.Alignment.RIGHT);
        assertThat(bigger.alignment(2)).isEqualTo(TableModel.Alignment.LEFT);
    }

    @Test
    void resizeCanShrinkTable() {
        TableModel smaller = TableModel.parse(SOURCE).resize(1, 1);

        assertThat(smaller.columnCount()).isEqualTo(1);
        assertThat(smaller.rowCount()).isEqualTo(1);
        assertThat(smaller.cell(0, 0)).isEqualTo("A");
        assertThat(TableModel.parse(smaller.source())).isNotNull();
    }

    @Test
    void prettySourceAlignsColumnsAndStaysParsable() {
        TableModel model = TableModel.parse("| a | longer text |\n|:---:|---:|\n| 1 | 2 |");
        String pretty = model.prettySource();
        String[] lines = pretty.split("\n");
        TableModel reparsed = TableModel.parse(pretty);

        assertThat(lines).hasSize(3);
        assertThat(lines[1]).hasSameSizeAs(lines[0]);
        assertThat(lines[2]).hasSameSizeAs(lines[0]);
        assertThat(reparsed).isNotNull();
        assertThat(reparsed.cell(0, 1)).isEqualTo("longer text");
        assertThat(reparsed.alignment(0)).isEqualTo(TableModel.Alignment.CENTER);
        assertThat(reparsed.alignment(1)).isEqualTo(TableModel.Alignment.RIGHT);
    }
}
