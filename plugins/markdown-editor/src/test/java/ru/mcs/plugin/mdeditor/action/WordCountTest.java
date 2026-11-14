package ru.mcs.plugin.mdeditor.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WordCountTest {

    @Test
    void countsUnicodeWordsCharactersLinesAndReadingTime() {
        WordCount.Stats stats = WordCount.count("Hello, мир!\nSecond line 😀");

        assertThat(stats.words()).isEqualTo(4);
        assertThat(stats.characters()).isEqualTo(25);
        assertThat(stats.lines()).isEqualTo(2);
        assertThat(stats.readingMinutes()).isEqualTo(1);
    }

    @Test
    void emptyTextHasZeroMetrics() {
        assertThat(WordCount.count("")).isEqualTo(new WordCount.Stats(0, 0, 0, 0));
        assertThat(WordCount.count(null)).isEqualTo(new WordCount.Stats(0, 0, 0, 0));
    }
}
