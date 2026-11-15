package ru.mcs.plugin.mdeditor.widget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageSourceTest {

    @Test
    void parsesMarkdownAndWikiResizeAndPreservesPath() {
        ImageSource markdown = ImageSource.parseBlock("![diagram|240](assets/my%20image.png)", 0,
                "![diagram|240](assets/my%20image.png)".length());
        assertThat(markdown.alt()).isEqualTo("diagram");
        assertThat(markdown.path()).isEqualTo("assets/my image.png");
        assertThat(markdown.width()).isEqualTo(240);
        assertThat(markdown.withWidth(320)).isEqualTo("![diagram|320](assets/my%20image.png)");

        ImageSource wiki = ImageSource.parseBlock("![[assets/picture.png|180]]", 0,
                "![[assets/picture.png|180]]".length());
        assertThat(wiki.wiki()).isTrue();
        assertThat(wiki.width()).isEqualTo(180);
        assertThat(wiki.withWidth(256)).isEqualTo("![[assets/picture.png|256]]");
    }
}
