package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TabularTextTest {

    @Test
    void excelRangeBecomesMarkdownTableWithNumericColumnsAlignedRight() {
        TableModel table = TabularText.toTable("Name\tQty\r\nApple\t3\r\nPear\t12\r\n");

        assertThat(table.source()).isEqualTo("| Name | Qty |\n| :--- | ---: |\n| Apple | 3 |\n| Pear | 12 |");
        assertThat(TableModel.parse(table.source())).isNotNull();
    }

    @Test
    void quotedCellsKeepNewlinesTabsAndDoubledQuotes() {
        TableModel table = TabularText.toTable("A\tB\n\"line1\nline2\"\t\"say \"\"hi\"\"\tx\"\n");

        assertThat(table.cell(1, 0)).isEqualTo("line1<br>line2");
        assertThat(table.cell(1, 1)).isEqualTo("say \"hi\" x");
    }

    @Test
    void pipesAreEscapedAndShortRowsPadded() {
        TableModel table = TabularText.toTable("a\tb\tc\nx|y\tz");

        assertThat(table.columnCount()).isEqualTo(3);
        assertThat(table.cell(1, 0)).isEqualTo("x\\|y");
        assertThat(table.cell(1, 2)).isEmpty();
    }

    @Test
    void plainTextWithoutTabsOrColumnsIsNotATable() {
        assertThat(TabularText.toTable("just text")).isNull();
        assertThat(TabularText.toTable("one\ntwo\n")).isNull();
        assertThat(TabularText.toTable("\t\n\t\n")).isNull();
        assertThat(TabularText.toTable("broken\t\"unterminated")).isNull();
        assertThat(TabularText.toTable(null)).isNull();
    }
}
