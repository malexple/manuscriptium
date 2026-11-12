package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownFileEditorProviderTest {

    @TempDir
    Path temp;

    @Test
    void acceptsMarkdownWithHigherPriorityThanCoreText() {
        MarkdownFileEditorProvider provider = provider();

        assertThat(provider.getId()).isEqualTo("markdown.editor");
        assertThat(provider.getPriority()).isGreaterThan(-100);
        assertThat(provider.accept(temp.resolve("note.md"))).isTrue();
        assertThat(provider.accept(temp.resolve("NOTE.MD"))).isTrue();
        assertThat(provider.accept(temp.resolve("note.txt"))).isFalse();
        assertThat(provider.accept(null)).isFalse();
    }

    @Test
    void createsEditorInsideNormalizedFileTab() throws Exception {
        Path file = temp.resolve("note.md");
        java.nio.file.Files.writeString(file, "# Heading");
        MarkdownEditorRegistry registry = new MarkdownEditorRegistry();
        MarkdownFileEditorProvider provider = new MarkdownFileEditorProvider(
                new MarkdownEditorState(), registry, ru.mcs.spi.i18n.I18n.passthrough());

        MarkdownFileEditor editor = (MarkdownFileEditor) provider.createEditor(file);
        try {
            assertThat(editor.getFile()).isEqualTo(file.toAbsolutePath().normalize());
            assertThat(editor.getName()).isEqualTo("note.md");
            assertThat(editor.getComponent()).isNotNull();
            assertThat(registry.find(file)).contains(editor);
        } finally {
            editor.dispose();
        }
    }

    private MarkdownFileEditorProvider provider() {
        return new MarkdownFileEditorProvider(new MarkdownEditorState(), new MarkdownEditorRegistry(),
                ru.mcs.spi.i18n.I18n.passthrough());
    }
}
