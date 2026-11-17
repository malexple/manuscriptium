package ru.mcs.markdown.editor.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.mcs.editor.model.Range;

class SelectionExpanderTest {

    @Test
    void expandsWordToAdjacentWordsAndThenWholePhysicalLine() {
        String text = "alpha beta gamma\nnext";
        Range beta = SelectionExpander.extend(text, new Range(7, 7), 7);
        assertThat(selected(text, beta)).isEqualTo("beta");

        Range betaGamma = SelectionExpander.extend(text, beta, beta.end());
        assertThat(selected(text, betaGamma)).isEqualTo("beta gamma");

        Range wholeLine = SelectionExpander.extend(text, betaGamma, betaGamma.end());
        assertThat(selected(text, wholeLine)).isEqualTo("alpha beta gamma");
        assertThat(SelectionExpander.extend(text, wholeLine, wholeLine.end())).isEqualTo(wholeLine);
        assertThat(text.charAt(wholeLine.end())).isEqualTo('\n');
    }

    @Test
    void supportsUnicodeWordsAndAlignsManualSelectionToWordBoundaries() {
        String text = "Привет мир";
        assertThat(selected(text, SelectionExpander.wordAt(text, 1))).isEqualTo("Привет");

        Range expanded = SelectionExpander.extend(text, new Range(2, 9), 9);
        assertThat(selected(text, expanded)).isEqualTo("Привет мир");
    }

    @Test
    void wordAtCaretAfterWordSelectsThatWordWithoutCrossingLines() {
        String text = "one\ntwo";
        assertThat(SelectionExpander.wordAt(text, 3)).isEqualTo(new Range(0, 3));
        assertThat(SelectionExpander.wordAt(text, 4)).isEqualTo(new Range(4, 7));
    }

    private static String selected(String text, Range range) {
        return text.substring(range.start(), range.end());
    }
}
