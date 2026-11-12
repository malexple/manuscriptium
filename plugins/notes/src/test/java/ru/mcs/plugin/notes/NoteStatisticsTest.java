package ru.mcs.plugin.notes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoteStatisticsTest {

    @Test
    void countsEmptyTextWithoutPhantomLine() {
        assertThat(NoteStatistics.count("")).isEqualTo(new NoteStatistics(0, 0, 0));
    }

    @Test
    void countsUnicodeWordsCharactersAndSourceLines() {
        NoteStatistics stats = NoteStatistics.count("# Привет, мир!\nJava 21 & snake_case\n");

        assertThat(stats.words()).isEqualTo(5);
        assertThat(stats.characters()).isEqualTo(36);
        assertThat(stats.lines()).isEqualTo(3);
    }

    @Test
    void supplementaryCodePointIsOneWordButTwoUtf16Characters() {
        assertThat(NoteStatistics.count("😀")).isEqualTo(new NoteStatistics(0, 2, 1));
        assertThat(NoteStatistics.count("𐐷")).isEqualTo(new NoteStatistics(1, 2, 1));
    }
}
