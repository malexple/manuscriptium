package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultPathsTest {

    @TempDir
    Path tmp;

    @Test
    void storesPortableRelativePathsAndServiceLocations() {
        Path file = tmp.resolve("notes").resolve("article.md");
        assertThat(VaultPaths.relativePath(tmp, file)).isEqualTo("notes/article.md");
        assertThat(VaultPaths.metadataDir(tmp)).isEqualTo(tmp.resolve(".manuscriptium"));
        assertThat(VaultPaths.stateDir(tmp)).isEqualTo(tmp.resolve(".manuscriptium/state"));
        assertThat(VaultPaths.databaseFile(tmp)).isEqualTo(tmp.resolve(".manuscriptium/index.db"));
    }

    @Test
    void excludesOnlyServiceAndGitInternals() {
        assertThat(VaultPaths.isExcluded(tmp, tmp.resolve(".manuscriptium/views/x.md"))).isTrue();
        assertThat(VaultPaths.isExcluded(tmp, tmp.resolve(".git/objects/x.md"))).isTrue();
        assertThat(VaultPaths.isExcluded(tmp, tmp.resolve("notes/.git.md"))).isFalse();
        assertThat(VaultPaths.isExcluded(tmp, tmp.resolve(".obsidian/note.md"))).isFalse();
    }

    @Test
    void detectsMarkdownCaseInsensitivelyAndRejectsOutsidePaths() {
        assertThat(VaultPaths.isMarkdown(tmp.resolve("NOTE.MD"))).isTrue();
        assertThat(VaultPaths.isMarkdown(tmp.resolve("note.markdown"))).isFalse();
        assertThatThrownBy(() -> VaultPaths.relativePath(tmp, tmp.resolveSibling("outside.md")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
