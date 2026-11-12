package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObsidianLinkResolverTest {

    @TempDir
    Path root;

    @Test
    void resolvesSiblingRootRelativeShortestAndCaseInsensitiveNotes() throws Exception {
        Path source = note("inbox/source.md");
        Path sibling = note("inbox/Target.md");
        note("archive/deep/Target.md");
        Path wiki = note("wiki/Note.md");
        Path deep = note("a/b/Deep.md");
        Path mixedCase = note("mixed/CamelCase.md");
        List<Path> notes = List.of(source, sibling, wiki, deep, mixedCase);

        assertThat(ObsidianLinkResolver.note(root, source, notes, "Target")).contains(sibling);
        assertThat(ObsidianLinkResolver.note(root, source, notes, "/wiki/Note")).contains(wiki);
        assertThat(ObsidianLinkResolver.note(root, source, notes, "../wiki/Note.md")).contains(wiki);
        assertThat(ObsidianLinkResolver.note(root, source, notes, "Deep")).contains(deep);
        assertThat(ObsidianLinkResolver.note(root, source, notes, "camelcase")).contains(mixedCase);
        assertThat(ObsidianLinkResolver.note(root, source, notes, "")).contains(source);
        assertThat(ObsidianLinkResolver.note(root, source, notes, "missing")).isEmpty();
    }

    @Test
    void choosesShortestThenLexicalMatchDeterministically() throws Exception {
        Path source = note("source.md");
        Path shortest = note("b/Target.md");
        Path deeper = note("a/c/Target.md");
        Path lexical = note("a/Target.md");
        assertThat(ObsidianLinkResolver.note(root, source, List.of(source, deeper, shortest), "target"))
                .contains(shortest);
        assertThat(ObsidianLinkResolver.note(root, source, List.of(source, deeper, lexical, shortest), "target"))
                .contains(lexical);
        assertThat(ObsidianLinkResolver.note(root, source, List.of(source, lexical), "a/target")).contains(lexical);
    }

    @Test
    void resolvesAttachmentsRelativeToNoteRootOrByUniqueFileName() throws Exception {
        Path source = note("inbox/source.md");
        Path local = attachment("inbox/local.png");
        Path asset = attachment("assets/root.png");
        Path deep = attachment("assets/nested/deep.svg");

        assertThat(ObsidianLinkResolver.attachment(root, source, "local.png")).contains(local);
        assertThat(ObsidianLinkResolver.attachment(root, source, "/assets/root.png")).contains(asset);
        assertThat(ObsidianLinkResolver.attachment(root, source, "deep.svg")).contains(deep);
        assertThat(ObsidianLinkResolver.attachment(root, source, "missing.png")).isEmpty();
    }

    @Test
    void rejectsEscapeOutsideVaultAndSymlinkTargets() throws Exception {
        Path source = note("inbox/source.md");
        Path outside = root.resolveSibling("outside.md");
        Files.writeString(outside, "# Outside");
        Path outsideDirectory = root.resolveSibling("outside-dir");
        Files.createDirectories(outsideDirectory);
        Path externalImage = Files.writeString(outsideDirectory.resolve("external.png"), "not an image");
        assumeSymlink(root.resolve("outside-link.md"), outside);
        assumeSymlink(root.resolve("linked-dir"), outsideDirectory);

        assertThat(ObsidianLinkResolver.note(root, source, List.of(source), "../../outside")).isEmpty();
        assertThat(ObsidianLinkResolver.note(root, source, List.of(source), "outside-link")).isEmpty();
        assertThat(ObsidianLinkResolver.attachment(root, source, "linked-dir/external.png")).isEmpty();
        assertThat(ObsidianLinkResolver.attachment(root, source, "external.png")).isEmpty();
        assertThat(externalImage).exists();
    }

    @Test
    void standaloneEditorIsRestrictedToSourceDirectory() throws Exception {
        Path source = note("inbox/source.md");
        Path sibling = note("inbox/Target.md");

        assertThat(ObsidianLinkResolver.note(null, source, List.of(), "Target")).contains(sibling);
        assertThat(ObsidianLinkResolver.note(null, source, List.of(), "../wiki/Note")).isEmpty();
    }

    private Path note(String relative) throws Exception {
        return attachment(relative);
    }

    private Path attachment(String relative) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "# " + file.getFileName());
        return file.toAbsolutePath().normalize();
    }

    private static void assumeSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (java.io.IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.assumeTrue(false, "Symbolic links are not available: " + e.getMessage());
        }
    }
}
