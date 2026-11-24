package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.mcs.markdown.widget.TableModel;

class TableInsertionTest {

    private static final String TABLE = "T";

    @Test
    void emptyDocumentGetsOnlyTrailingBlankLine() {
        TableInsertion insertion = TableInsertion.plan("", 0, 0, TABLE);

        assertThat(insertion.replacement()).isEqualTo("T\n\n");
        assertThat(insertion.caretOffset()).isEqualTo(2);
    }

    @Test
    void tableAfterParagraphIsSeparatedByBlankLines() {
        TableInsertion insertion = TableInsertion.plan("Body", 4, 4, TABLE);

        assertThat(insertion.replacement()).isEqualTo("\n\nT\n\n");
        assertThat(insertion.caretOffset()).isEqualTo(4);
    }

    @Test
    void existingBlankLinesAreNotDuplicated() {
        assertThat(TableInsertion.plan("Body\n\n", 6, 6, TABLE).replacement()).isEqualTo("T\n\n");
        assertThat(TableInsertion.plan("Body\n", 5, 5, TABLE).replacement()).isEqualTo("\nT\n\n");
        assertThat(TableInsertion.plan("A\n\nB", 3, 3, TABLE).replacement()).isEqualTo("T\n\n");
    }

    @Test
    void selectionIsReplacedAndNeighboursKeepBlankLines() {
        TableInsertion insertion = TableInsertion.plan("A\n\nxx\n\nB", 3, 5, TABLE);

        assertThat(insertion.replacement()).isEqualTo("T");
        assertThat(insertion.caretOffset()).isEqualTo(2);
    }

    @Test
    void blankTableIsParsableAndKeepsRequestedShape() {
        String source = TableModel.blank(3, 4).source();
        TableModel parsed = TableModel.parse(source);

        assertThat(parsed).isNotNull();
        assertThat(parsed.columnCount()).isEqualTo(3);
        assertThat(parsed.rowCount()).isEqualTo(4);
        assertThat(source).startsWith("|  |  |  |\n| :--- | :--- | :--- |\n|  |  |  |");
        assertThat(TableModel.blank(0, 0).source()).isEqualTo("|  |\n| :--- |");
        assertThat(TableModel.blank(999, 999).columnCount()).isEqualTo(TableModel.MAX_COLUMNS);
        assertThat(TableModel.blank(999, 999).rowCount()).isEqualTo(TableModel.MAX_ROWS);
    }
}
