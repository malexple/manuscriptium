package ru.mcs.core.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPathsTest {

    @TempDir
    Path tmp;

    @Test
    void storesPortableRelativePathsAndServiceLocations() {
        Path file = tmp.resolve("notes").resolve("article.md");
        assertThat(ProjectPaths.relativePath(tmp, file)).isEqualTo("notes/article.md");
        assertThat(ProjectPaths.metadataDir(tmp)).isEqualTo(tmp.resolve(".manuscriptium"));
        assertThat(ProjectPaths.stateDir(tmp)).isEqualTo(tmp.resolve(".manuscriptium/state"));
        assertThat(ProjectPaths.databaseFile(tmp)).isEqualTo(tmp.resolve(".manuscriptium/index.db"));
    }

    @Test
    void excludesOnlyServiceAndGitInternals() {
        assertThat(ProjectPaths.isExcluded(tmp, tmp.resolve(".manuscriptium/views/x.md"))).isTrue();
        assertThat(ProjectPaths.isExcluded(tmp, tmp.resolve(".git/objects/x.md"))).isTrue();
        assertThat(ProjectPaths.isExcluded(tmp, tmp.resolve("notes/.git.md"))).isFalse();
        assertThat(ProjectPaths.isExcluded(tmp, tmp.resolve(".settings/note.txt"))).isFalse();
    }

    @Test
    void rejectsOutsidePaths() {
        assertThatThrownBy(() -> ProjectPaths.relativePath(tmp, tmp.resolveSibling("outside.md")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
