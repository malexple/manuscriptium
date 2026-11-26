package ru.mcs.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YamlFormatterTest {

    private static final YamlStyle DEFAULT = YamlStyle.defaults();

    @Test
    void trimsSpacesCapsBlankLinesAndEndsWithNewline() {
        assertThat(YamlFormatter.format("a: 1  \nb:\t2\n\n\n\n\nc: 3", DEFAULT))
                .isEqualTo("a: 1\nb:\t2\n\n\nc: 3\n");
        assertThat(YamlFormatter.format("a: 1\n\n\n", DEFAULT)).isEqualTo("a: 1\n");
    }

    @Test
    void replacesLeadingTabsOnly() {
        assertThat(YamlFormatter.format("a:\n\tb: 1\n", DEFAULT)).isEqualTo("a:\n  b: 1\n");
        YamlStyle keepTabs = new YamlStyle(2, false, true, true, 2);
        assertThat(YamlFormatter.format("a:\n\tb: 1\n", keepTabs)).isEqualTo("a:\n\tb: 1\n");
    }

    @Test
    void blockScalarContentIsPreservedExactly() {
        String text = "k: |\n  text  \n\n\n\n\n  more\nz: 1  \n";
        assertThat(YamlFormatter.format(text, DEFAULT)).isEqualTo("k: |\n  text  \n\n\n\n\n  more\nz: 1\n");
        String list = "- >-\n    folded  \n    text\n";
        assertThat(YamlFormatter.format(list, DEFAULT)).isEqualTo(list);
    }

    @Test
    void optionsAreHonoredAndResultIsStable() {
        YamlStyle noFinal = new YamlStyle(2, true, true, false, 1);
        assertThat(YamlFormatter.format("a: 1", noFinal)).isEqualTo("a: 1");
        assertThat(YamlFormatter.format("a: 1\n\n\n\nb: 2\n", noFinal)).isEqualTo("a: 1\n\nb: 2\n");
        assertThat(YamlFormatter.format("a: 1  \r\nb: 2\r\n", DEFAULT)).isEqualTo("a: 1\r\nb: 2\r\n");
        String once = YamlFormatter.format("a:\t1  \n\n\n\n\nb:\n\t- x  \n", DEFAULT);
        assertThat(YamlFormatter.format(once, DEFAULT)).isEqualTo(once);
        assertThat(YamlFormatter.format("", DEFAULT)).isEmpty();
    }
}
