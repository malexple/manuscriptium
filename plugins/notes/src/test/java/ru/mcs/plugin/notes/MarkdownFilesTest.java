package ru.mcs.plugin.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MarkdownFilesTest {

    @Test
    void recognizesMarkdownCaseInsensitively() {
        assertThat(MarkdownFiles.isMarkdown(Path.of("Note.md"))).isTrue();
        assertThat(MarkdownFiles.isMarkdown(Path.of("Note.MD"))).isTrue();
        assertThat(MarkdownFiles.isMarkdown(Path.of("Note.markdown"))).isFalse();
        assertThat(MarkdownFiles.isMarkdown(null)).isFalse();
    }

    @Test
    void createsShortObsidianLinkWithoutChangingFile() {
        Path file = Path.of("vault", "folder", "My note.MD");

        assertThat(MarkdownFiles.wikiLink(file)).isEqualTo("[[My note]]");
        assertThat(file).isEqualTo(Path.of("vault", "folder", "My note.MD"));
    }

    @Test
    void rejectsNonMarkdownLink() {
        assertThatIllegalArgumentException().isThrownBy(() -> MarkdownFiles.wikiLink(Path.of("image.png")));
    }
}
