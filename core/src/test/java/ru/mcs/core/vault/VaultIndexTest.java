package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultIndexTest {

    @TempDir
    Path tmp;

    @Test
    void rebuildsCacheFromMarkdownAndIgnoresServiceFiles() throws Exception {
        Files.createDirectories(tmp.resolve("notes"));
        Files.writeString(tmp.resolve("notes/article.md"), "---\ntitle: 'My article'\ntags: [java]\n---\nBody\n");
        Files.writeString(tmp.resolve("root.md"), "# Root heading\nText");
        Files.writeString(tmp.resolve("plain.txt"), "not indexed");
        Files.createDirectories(tmp.resolve(".manuscriptium/views"));
        Files.writeString(tmp.resolve(".manuscriptium/views/ignored.md"), "# Ignored");
        Files.createDirectories(tmp.resolve(".git"));
        Files.writeString(tmp.resolve(".git/ignored.md"), "# Ignored");

        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(index.count()).isEqualTo(2);
            assertThat(index.notes()).extracting(VaultNote::path)
                    .containsExactly("notes/article.md", "root.md");
            MarkdownDocument article = index.find("notes/article.md").orElseThrow();
            assertThat(article.title()).isEqualTo("My article");
            assertThat(article.frontMatter()).contains("tags: [java]");
            assertThat(article.content()).endsWith("Body\n");
            assertThat(index.find("root.md").orElseThrow().title()).isEqualTo("Root heading");
        }
    }

    @Test
    void incrementallyUpdatesFilesAndDeletedTrees() throws Exception {
        Files.createDirectories(tmp.resolve("tree"));
        Path note = tmp.resolve("tree/note.md");
        Files.writeString(note, "# Before");
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            Files.writeString(note, "---\ntitle: After\n---\nnew body");
            index.refresh(note);
            assertThat(index.find("tree/note.md").orElseThrow().title()).isEqualTo("After");
            assertThat(index.find("tree/note.md").orElseThrow().content()).contains("new body");

            Files.delete(note);
            index.refresh(note);
            assertThat(index.count()).isZero();

            Files.createDirectories(tmp.resolve("tree/sub"));
            Files.writeString(tmp.resolve("tree/sub/a.md"), "a");
            Files.writeString(tmp.resolve("tree/sub/b.md"), "b");
            index.rebuild();
            assertThat(index.count()).isEqualTo(2);
            index.refresh(tmp.resolve("tree"));
            assertThat(index.count()).isEqualTo(2);
            index.refresh(tmp.resolve("tree/sub"));
            assertThat(index.count()).isEqualTo(2);

            Files.delete(tmp.resolve("tree/sub/a.md"));
            Files.delete(tmp.resolve("tree/sub/b.md"));
            Files.delete(tmp.resolve("tree/sub"));
            index.refresh(tmp.resolve("tree/sub"));
            assertThat(index.count()).isZero();
        }
    }

    @Test
    void deletingDirectoryDoesNotDeleteSqlLikeSimilarPaths() throws Exception {
        Files.createDirectories(tmp.resolve("dir_name"));
        Files.createDirectories(tmp.resolve("dirXname"));
        Files.createDirectories(tmp.resolve("dir%name"));
        Files.createDirectories(tmp.resolve("dirZname"));
        Files.writeString(tmp.resolve("dir_name/a.md"), "a");
        Files.writeString(tmp.resolve("dirXname/b.md"), "b");
        Files.writeString(tmp.resolve("dir%name/c.md"), "c");
        Files.writeString(tmp.resolve("dirZname/d.md"), "d");
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            Files.delete(tmp.resolve("dir_name/a.md"));
            Files.delete(tmp.resolve("dir_name"));
            index.refresh(tmp.resolve("dir_name"));
            assertThat(index.notes()).extracting(VaultNote::path)
                    .containsExactly("dir%name/c.md", "dirXname/b.md", "dirZname/d.md");

            Files.delete(tmp.resolve("dir%name/c.md"));
            Files.delete(tmp.resolve("dir%name"));
            index.refresh(tmp.resolve("dir%name"));
            assertThat(index.notes()).extracting(VaultNote::path)
                    .containsExactly("dirXname/b.md", "dirZname/d.md");
        }
    }

    @Test
    void deletedDatabaseIsRecreatedWithoutAnyMetadataSource() throws Exception {
        Files.writeString(tmp.resolve("portable.md"), "# Portable\nonly markdown matters");
        Path db = VaultPaths.databaseFile(tmp);
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(db).exists();
        }
        try (var paths = Files.list(VaultPaths.metadataDir(tmp))) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
        try (VaultIndex index = new VaultIndex(tmp)) {
            assertThat(index.count()).isZero();
            index.rebuild();
            assertThat(index.count()).isOne();
            assertThat(index.find("portable.md").orElseThrow().title()).isEqualTo("Portable");
        }
    }

    @Test
    void unreadableUtf8DoesNotAbortWholeScan() throws Exception {
        Files.writeString(tmp.resolve("good.md"), "good");
        Files.write(tmp.resolve("bad.md"), new byte[]{(byte) 0xC3, (byte) 0x28});
        try (VaultIndex index = new VaultIndex(tmp)) {
            index.rebuild();
            assertThat(index.count()).isOne();
            assertThat(index.notes().getFirst().path()).isEqualTo("good.md");
        }
    }
}
